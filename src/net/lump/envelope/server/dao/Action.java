package net.lump.envelope.server.dao;

import net.lump.envelope.shared.entity.Account;
import net.lump.envelope.shared.entity.Allocation;
import net.lump.envelope.shared.entity.AllocationPreset;
import net.lump.envelope.shared.entity.Budget;
import net.lump.envelope.shared.entity.Category;
import net.lump.envelope.shared.entity.Transaction;
import net.lump.envelope.shared.entity.User;
import net.lump.envelope.shared.command.security.Permission;
import net.lump.envelope.shared.command.security.Scram;
import net.lump.envelope.shared.exception.EnvelopeException;
import net.lump.lib.Money;
import org.hibernate.Hibernate;
import org.hibernate.LockMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * A DAO for Transactions.
 *
 * @author Troy Bowman
 */
public class Action extends DAO {

  /**
   * Sets reconciled -- the transaction list's checkbox.
   *
   * <p>Returns the transaction's new version stamp.  The change moves it, and the
   * transaction form may be holding this transaction with the old one: it used
   * to keep that, so its next save of the transaction was refused as stale (and
   * before 0.12 went through and put reconciled back as the form remembered it).
   * The client hands the stamp and the new value to the form.
   *
   * @param transactionID the ID of the transaction
   * @param reconciled whether this is reconciled or not
   * @return the stamp the transaction carries now
   */
  public java.sql.Timestamp updateReconciled(Integer transactionID, Boolean reconciled) throws EnvelopeException {
    requireMyTransaction(transactionID);
    Transaction t = get(Transaction.class, transactionID);
    t.setReconciled(Boolean.TRUE.equals(reconciled));
    getCurrentSession().flush();   // the version moves on the flush
    return t.getStamp();
  }

  /**
   * Delete one Allocation.
   *
   * <p>Deliberately narrow rather than a generic "delete this entity" command:
   * this one can only ever remove an allocation, only in the caller's budget, and
   * it enforces the invariant below no matter what the client believes.
   *
   * @param allocationId the id of the Allocation to remove
   *
   * @throws EnvelopeException if this is the transaction's last allocation
   */
  public void deleteAllocation(Integer allocationId) throws EnvelopeException {
    requireMyAllocation(allocationId);
    Allocation allocation = load(Allocation.class, allocationId);
    Integer transactionId = allocation.getTransaction().getId();

    // Count the siblings with a LOCKING read, which does two things that a plain
    // one does not.  It serializes concurrent deletes against the same
    // transaction, and it reads the latest committed rows: under REPEATABLE READ
    // an ordinary SELECT would answer from the snapshot taken before we waited,
    // so a second caller would still see the row the first one just removed.
    // Without it this is check-then-act -- two deletes on a two-allocation
    // transaction each see two, each pass, and the transaction is left with none.
    // Deleting a child never touches the parent's @Version, so optimistic locking
    // offers nothing here.
    List<Allocation> siblings = getCurrentSession()
        .createSelectionQuery("from Allocation a where a.transaction.id = :transactionId", Allocation.class)
        .setParameter("transactionId", transactionId)
        .setHibernateLockMode(LockMode.PESSIMISTIC_WRITE)
        .list();

    // A transaction reaches its budget only through its allocations -- the
    // transactions table carries no budget column -- so a transaction with none
    // is invisible to every query the client can make, and its row is stranded.
    // Removing the last allocation means deleting the transaction, which is a
    // different operation.
    if (siblings.size() <= 1)
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data,
          "a transaction must keep at least one allocation");

    delete(allocation);
  }

  /**
   * Create a transaction together with its first allocation.
   *
   * <p>The two are made in one go on purpose.  A transaction carries no budget
   * column and is reachable only through its allocations, so one saved on its own
   * -- even for the moment between two round trips -- is a row no query can find.
   * Controller commits this method as a single database transaction, so either
   * both rows exist or neither does.
   *
   * @param categoryId  the category the first allocation belongs to
   * @param date        the transaction date
   * @param entity      who it was paid to or received from
   * @param description free text
   * @param amount      the first allocation's amount
   *
   * @return the new Transaction, detached, with its allocation loaded
   */
  public Transaction createTransaction(Integer categoryId, Date date, String entity,
                                       String description, Money amount)
      throws EnvelopeException {
    if (categoryId == null)
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data, "a transaction needs a category");
    requireMyCategory(categoryId);

    Category category = load(Category.class, categoryId);

    Transaction transaction = new Transaction();
    transaction.setDate(date);
    transaction.setEntity(entity == null ? "" : entity);
    transaction.setDescription(description == null ? "" : description);
    transaction.setReconciled(false);
    transaction.setTransfer(false);

    Allocation allocation = new Allocation();
    allocation.setTransaction(transaction);
    allocation.setCategory(category);
    allocation.setAmount(amount == null ? Money.ZERO : amount);

    getCurrentSession().persist(transaction);
    getCurrentSession().persist(allocation);
    getCurrentSession().flush();

    // re-read so the mapped allocations collection reflects what was just written,
    // then detach it the way Generic.get does before it goes over the wire
    getCurrentSession().refresh(transaction);
    Hibernate.initialize(transaction);
    evict(transaction);
    return transaction;
  }

  /**
   * Delete a transaction and every allocation on it.
   *
   * <p>The allocations go first and in the same database transaction: their
   * foreign key is ON DELETE RESTRICT and the inverse side carries no cascade, so
   * deleting the parent while children remain simply fails.  The children are
   * taken with a locking read so a concurrent deleteAllocation cannot interleave.
   *
   * @param transactionId the transaction to remove
   */
  public void deleteTransaction(Integer transactionId) throws EnvelopeException {
    requireMyTransaction(transactionId);
    Transaction transaction = load(Transaction.class, transactionId);

    List<Allocation> allocations = getCurrentSession()
        .createSelectionQuery("from Allocation a where a.transaction.id = :transactionId", Allocation.class)
        .setParameter("transactionId", transactionId)
        .setHibernateLockMode(LockMode.PESSIMISTIC_WRITE)
        .list();

    for (Allocation allocation : allocations) delete(allocation);
    getCurrentSession().flush();

    delete(transaction);
  }

  // ------------------------------------------------------------------------
  // Saving what the form edits.
  //
  // These replace the generic saveOrUpdate, which took any Identifiable at all
  // -- a User with ADMIN permission included -- and wrote it.  They take plain
  // values, not entities: the row's id, the version stamp the client last saw,
  // and the fields the form edits.  The server loads its own row, refuses a stale
  // stamp exactly as @Version would, and copies the fields across.
  //
  // Values rather than the client's object, because reattaching a client's object
  // graph hands Hibernate whatever the client put in it.  The first cut of 0.12
  // did that, and an independent review read another budget's allocations
  // through it: a Transaction sent for saving carried a PersistentBag whose
  // collection key the client had set to someone else's transaction id, and
  // reattaching it loaded that collection into the reply.  Nothing the client
  // sends is attached to the session now, so no command needs to carry an entity
  // or a Hibernate collection, and WireFilter admits neither.
  // ------------------------------------------------------------------------

  /** Refuse a save made against an older version of the row than the one stored. */
  private void requireCurrent(java.sql.Timestamp stored, java.sql.Timestamp seen, String what)
      throws EnvelopeException {
    if (seen == null || stored == null || !stored.equals(seen))
      throw new EnvelopeException(EnvelopeException.Name.Invalid_Data,
          what + " was changed since it was read; reload it and try again");
  }

  /**
   * Save a transaction's own columns.  New transactions come from
   * createTransaction, which makes the first allocation with them; one saved
   * alone is a row no query can find.
   *
   * @return the saved transaction, carrying its new version stamp
   */
  public Transaction saveTransaction(Integer transactionId, java.sql.Timestamp stamp, java.util.Date date,
                                     String entity, String description, Boolean reconciled, Boolean transfer)
      throws EnvelopeException {
    requireMyTransaction(transactionId);
    if (date == null)
      throw new EnvelopeException(EnvelopeException.Name.Invalid_Data, "a transaction needs a date");
    Transaction transaction = get(Transaction.class, transactionId);
    requireCurrent(transaction.getStamp(), stamp, "transaction " + transactionId);

    transaction.setDate(new Date(date.getTime()));
    transaction.setEntity(entity == null ? "" : entity);
    transaction.setDescription(description == null ? "" : description);
    transaction.setReconciled(Boolean.TRUE.equals(reconciled));
    transaction.setTransfer(Boolean.TRUE.equals(transfer));
    getCurrentSession().flush();
    Hibernate.initialize(transaction);
    return evict(transaction);
  }

  /**
   * Save an allocation, inserting it when its id is null.  Its category and its
   * transaction must both be the caller's.  An existing allocation stays on its
   * transaction: moving one would be a way to take a transaction's last
   * allocation without deleteAllocation's guard, and the form never does it.
   *
   * @return the saved allocation, carrying its id and new version stamp
   */
  public Allocation saveAllocation(Integer allocationId, java.sql.Timestamp stamp, Integer transactionId,
                                   Integer categoryId, Money amount)
      throws EnvelopeException {
    requireMyTransaction(transactionId);
    requireMyCategory(categoryId);

    Allocation allocation;
    if (allocationId == null) {
      allocation = new Allocation();
      allocation.setTransaction(get(Transaction.class, transactionId));
    }
    else {
      requireMyAllocation(allocationId);
      allocation = get(Allocation.class, allocationId);
      requireCurrent(allocation.getStamp(), stamp, "allocation " + allocationId);
      if (!allocation.getTransaction().getId().equals(transactionId))
        throw new EnvelopeException(EnvelopeException.Name.Invalid_Data,
            "an allocation cannot be moved to another transaction");
    }
    allocation.setCategory(get(Category.class, categoryId));
    allocation.setAmount(amount == null ? Money.ZERO : amount);

    if (allocation.getId() == null) getCurrentSession().persist(allocation);
    getCurrentSession().flush();
    Hibernate.initialize(allocation);
    return evict(allocation);
  }

  /**
   * Save an allocation preset row in the caller's budget, inserting it when its
   * id is null.
   *
   * @param type one of AllocationPreset.AllocationType -- percent or fixed
   * @return the saved row, carrying its id and new version stamp
   */
  public AllocationPreset savePreset(Integer presetRowId, java.sql.Timestamp stamp, String name,
                                     Integer categoryId, BigDecimal allocation, String type, Boolean autoDeduct)
      throws EnvelopeException {
    requireMyCategory(categoryId);
    String presetName = checkName(name);
    // a bare BigDecimal, so Money's own check on the wire does not cover it
    if (!Money.isSane(allocation))
      throw new EnvelopeException(EnvelopeException.Name.Invalid_Data, "that allocation is out of all proportion");
    AllocationPreset.AllocationType allocationType;
    try {
      allocationType = AllocationPreset.AllocationType.valueOf(type);
    } catch (RuntimeException e) {
      throw new EnvelopeException(EnvelopeException.Name.Invalid_Data, "\"" + type + "\" is not a preset type");
    }

    AllocationPreset row;
    if (presetRowId == null) {
      row = new AllocationPreset();
      row.setBudget(get(Budget.class, myBudgetId()));
    }
    else {
      requireMyPresetRow(presetRowId);
      row = get(AllocationPreset.class, presetRowId);
      requireCurrent(row.getStamp(), stamp, "preset row " + presetRowId);
    }
    row.setName(presetName);
    row.setCategory(get(Category.class, categoryId));
    row.setAllocation(allocation);
    row.setAllocationType(allocationType);
    row.setAutoDeduct(Boolean.TRUE.equals(autoDeduct));

    if (row.getId() == null) getCurrentSession().persist(row);
    getCurrentSession().flush();
    Hibernate.initialize(row);
    return evict(row);
  }

  /**
   * Put one amount on the transaction, reusing a row that carries no money before
   * making a new one.  A transaction is born with a single zero allocation, and
   * leaving that stranded beside the preset's rows is just litter.
   */
  private void place(Transaction transaction, Category category, Money amount,
                     LinkedList<Allocation> spare) {
    Allocation allocation;
    if (spare.isEmpty()) {
      allocation = new Allocation();
      allocation.setTransaction(transaction);
    }
    else allocation = spare.removeFirst();

    allocation.setCategory(category);
    allocation.setAmount(amount);
    // a spare is already managed, and its changes are flushed with the rest
    if (allocation.getId() == null) getCurrentSession().persist(allocation);
  }

  /**
   * Lay a named preset over a transaction, sized against a gross amount.
   *
   * <p>Each row contributes one allocation, except an auto-deduct row, which
   * contributes a pair: the amount in and the same amount straight back out.  That
   * is what actually happened -- the employee was paid and the money was removed
   * before it ever arrived -- and recording both halves is what keeps the payment
   * visible in the category's history while netting to nothing on the account.
   *
   * <p>Rows worth nothing are skipped rather than written as $0.00 allocations.
   *
   * <p>The result is not expected to balance: the allocations are what the preset
   * says, and reconciling them against the transaction amount -- usually by
   * putting the remainder somewhere like Stash or Savings -- is the user's to do,
   * with the form's imbalance panel showing the gap.
   *
   * @param transactionId the transaction to lay the preset over
   * @param presetName    which preset
   * @param gross         the amount percentages are taken against
   *
   * @return the transaction, with its allocations as they now stand
   */
  public Transaction applyAllocationPreset(Integer transactionId, String presetName, Money gross)
      throws EnvelopeException {
    requireMyTransaction(transactionId);
    Transaction transaction = load(Transaction.class, transactionId);

    List<Allocation> existing = getCurrentSession()
        .createSelectionQuery("from Allocation a where a.transaction.id = :transactionId", Allocation.class)
        .setParameter("transactionId", transactionId)
        .setHibernateLockMode(LockMode.PESSIMISTIC_WRITE)
        .list();

    // a transaction reaches its budget only through its allocations, so that is
    // the only place the budget can be read from
    if (existing.isEmpty())
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data,
          "transaction " + transactionId + " has no allocations to take a budget from");
    Budget budget = existing.get(0).getCategory().getAccount().getBudget();

    List<AllocationPreset> rows = getCurrentSession()
        .createSelectionQuery("from AllocationPreset p where p.budget.id = :budgetId and p.name = :name", AllocationPreset.class)
        .setParameter("budgetId", budget.getId())
        .setParameter("name", presetName)
        .list();
    if (rows.isEmpty())
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data,
          "this budget has no preset called \"" + presetName + "\"");

    LinkedList<Allocation> spare = new LinkedList<Allocation>();
    for (Allocation a : existing)
      if (a.getAmount() == null || a.getAmount().compareTo(Money.ZERO) == 0) spare.add(a);

    for (AllocationPreset preset : rows) {
      Money value = preset.valueFor(gross);
      if (value.compareTo(Money.ZERO) == 0) continue;

      place(transaction, preset.getCategory(), value, spare);
      if (preset.isAutoDeduct())
        place(transaction, preset.getCategory(), value.negate(), spare);
    }

    getCurrentSession().flush();
    getCurrentSession().refresh(transaction);
    Hibernate.initialize(transaction);
    evict(transaction);
    return transaction;
  }

  // ------------------------------------------------------------------------
  // Users and budgets.
  //
  // These are the first commands that care WHO is asking.  Controller loads the
  // authenticated user into ThreadInfo before dispatch; getUser() reads it back.
  // ADMIN manages users and budgets; anyone may change their own password.
  //
  // Nothing here ever returns a User with its password hash in it.  A User goes
  // over the wire as a copy carrying only what the client needs to show.
  // ------------------------------------------------------------------------

  private void requireAdmin() throws EnvelopeException {
    if (!getUser().getPermission().hasPermission(Permission.ADMIN))
      throw new EnvelopeException(
          EnvelopeException.Name.Permission_Denied, "that needs administrator permission");
  }

  /**
   * A copy of a User safe to send to a client: identity, name, budget and
   * permissions, and nothing that could stand in for a credential.  A fresh
   * object rather than the loaded one with fields blanked, because blanking a
   * field on an attached entity is an update Hibernate would happily write.
   */
  private User forClient(User user) {
    User out = new User();
    out.setId(user.getId());
    out.setStamp(user.getStamp());
    out.setName(user.getName());
    out.setRealName(user.getRealName());
    out.setBudget(user.getBudget());
    out.setPermission(new Permission(user.getPermission()));
    return out;
  }

  /** Who the caller is: their name, budget and permissions. */
  public User whoAmI() {
    return forClient(getUser());
  }

  /** Every user.  ADMIN only. */
  @SuppressWarnings("unchecked")
  public List<User> listUsers() throws EnvelopeException {
    requireAdmin();
    List<User> users = getCurrentSession()
        .createSelectionQuery("from User u order by u.name", User.class).list();
    List<User> out = new ArrayList<User>(users.size());
    for (User u : users) out.add(forClient(u));
    return out;
  }

  /** Every budget.  ADMIN only. */
  @SuppressWarnings("unchecked")
  public List<Budget> listBudgets() throws EnvelopeException {
    requireAdmin();
    List<Budget> budgets = getCurrentSession()
        .createSelectionQuery("from Budget b order by b.name", Budget.class).list();
    for (Budget b : budgets) evict(b);
    return budgets;
  }

  /**
   * Add a user.  ADMIN only.
   *
   * @param name          login name, unique across the installation
   * @param realName      display name
   * @param budgetId      the one budget the user works in
   * @param permissions   a Permission bitmask
   * @param md5Salt..serverKey the initial password's SCRAM verifier, made by the client
   */
  public User createUser(String name, String realName, Integer budgetId, Long permissions,
                         String md5Salt, byte[] salt, Integer iterations, byte[] storedKey, byte[] serverKey)
      throws EnvelopeException {
    requireAdmin();
    String userName = checkName(name);

    long taken = ((Number)getCurrentSession()
        .createSelectionQuery("select count(u) from User u where u.name = :name", Long.class)
        .setParameter("name", userName).uniqueResult()).longValue();
    if (taken > 0)
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data, "there is already a user called \"" + userName + "\"");

    Budget budget = get(Budget.class, budgetId);
    if (budget == null)
      throw new EnvelopeException(EnvelopeException.Name.Invalid_Data, "there is no budget " + budgetId);
    Scram.Verifier verifier = verifier(md5Salt, salt, iterations, storedKey, serverKey);

    User user = new User();
    user.setName(userName);
    user.setRealName(realName == null ? "" : realName.trim());
    user.setBudget(budget);
    user.setPermission(new Permission(permissions == null ? 0L : permissions));
    user.setVerifier(verifier);

    getCurrentSession().persist(user);
    getCurrentSession().flush();
    return forClient(user);
  }

  /**
   * Change a user's name, budget or permissions.  ADMIN only, and an admin may
   * not take ADMIN away from themself -- the way out of that is another admin.
   */
  public void updateUser(Integer userId, String realName, Integer budgetId, Long permissions)
      throws EnvelopeException {
    requireAdmin();
    User user = get(User.class, userId);
    if (user == null)
      throw new EnvelopeException(EnvelopeException.Name.Invalid_Data, "there is no user " + userId);

    Permission wanted = new Permission(permissions == null ? 0L : permissions);
    if (user.getId().equals(getUser().getId()) && !wanted.hasPermission(Permission.ADMIN))
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data, "you cannot remove your own administrator permission");

    Budget budget = get(Budget.class, budgetId);
    if (budget == null)
      throw new EnvelopeException(EnvelopeException.Name.Invalid_Data, "there is no budget " + budgetId);

    user.setRealName(realName == null ? "" : realName.trim());
    user.setBudget(budget);
    user.setPermission(wanted);
    getCurrentSession().flush();   // see renameAccount
  }

  /**
   * Set a user's password.  ADMIN may set anyone's; anyone may set their own.
   *
   * @param userId        whose
   */
  /**
   * Set a password, as the verifier the client computed from it.  An
   * administrator may set anyone's; anyone may set their own, but only with a
   * proof made with the current password -- a session left open is not enough to
   * take an account over.  The proof is an ordinary SCRAM exchange: scramStart
   * for one's own name, then the proof for it here instead of in scramFinish.
   * A wrong one counts against the login throttle like a failed login.
   */
  public void setPassword(Integer userId, String md5Salt, byte[] salt, Integer iterations, byte[] storedKey,
                          byte[] serverKey, String nonce, byte[] currentProof, java.security.PublicKey proofKey)
      throws EnvelopeException {
    User caller = getUser();
    boolean self = caller.getId().equals(userId);
    if (!self && !caller.getPermission().hasPermission(Permission.ADMIN))
      throw new EnvelopeException(
          EnvelopeException.Name.Permission_Denied, "only an administrator can set another user's password");
    Scram.Verifier verifier = verifier(md5Salt, salt, iterations, storedKey, serverKey);

    User user = self ? caller : get(User.class, userId);
    if (user == null)
      throw new EnvelopeException(EnvelopeException.Name.Invalid_Data, "there is no user " + userId);

    if (self) {
      String address = net.lump.envelope.server.ThreadInfo.getRemoteAddress();
      if (Logins.waitFor(user.getName(), address) > 0)
        throw new EnvelopeException(EnvelopeException.Name.Invalid_Credentials, "too many failed attempts; try again later");
      Logins.Exchange exchange = Logins.finish(nonce);
      boolean proven = false;
      try {
        proven = exchange != null && proofKey != null && user.getName().equals(exchange.username)
            && user.getScramStoredKey() != null
            && Scram.proofIsValid(user.getScramStoredKey(),
                   Scram.authMessage(exchange.username, exchange.clientNonce, nonce, exchange.salt, exchange.iterations, proofKey),
                   currentProof);
      } catch (java.security.GeneralSecurityException e) {
        proven = false;
      }
      if (!proven) {
        Logins.failed(user.getName(), address);
        throw new EnvelopeException(EnvelopeException.Name.Invalid_Credentials, "the current password is not right");
      }
    }

    user.setVerifier(verifier);
    getCurrentSession().flush();   // see renameAccount
  }

  /**
   * A verifier from its parts, refusing what could not have come from
   * Scram.newVerifier: a malformed md5-crypt salt, a salt or key of the wrong
   * size, or an iteration count too cheap to guess against or so dear it would
   * make every login hang.
   */
  private static Scram.Verifier verifier(String md5Salt, byte[] salt, Integer iterations, byte[] storedKey,
                                         byte[] serverKey) throws EnvelopeException {
    if (md5Salt == null || !md5Salt.matches("[./0-9A-Za-z]{8}")
        || salt == null || salt.length < Scram.SALT_BYTES || salt.length > 32
        || iterations == null || iterations < Scram.MIN_ITERATIONS || iterations > Scram.MAX_ITERATIONS
        || storedKey == null || storedKey.length != Scram.KEY_BYTES
        || serverKey == null || serverKey.length != Scram.KEY_BYTES)
      throw new EnvelopeException(EnvelopeException.Name.Invalid_Data, "that is not a password verifier");
    return new Scram.Verifier(md5Salt, salt, iterations, storedKey, serverKey);
  }

  /** Add a budget.  ADMIN only. */
  public Budget createBudget(String name) throws EnvelopeException {
    requireAdmin();
    String budgetName = checkName(name);

    long taken = ((Number)getCurrentSession()
        .createSelectionQuery("select count(b) from Budget b where b.name = :name", Long.class)
        .setParameter("name", budgetName).uniqueResult()).longValue();
    if (taken > 0)
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data, "there is already a budget called \"" + budgetName + "\"");

    Budget budget = new Budget();
    budget.setName(budgetName);
    getCurrentSession().persist(budget);
    getCurrentSession().flush();
    return evict(budget);
  }

  /**
   * Remove a budget, which must have nothing in it.  ADMIN only.  Accounts and
   * users both reference budgets ON DELETE RESTRICT, so the foreign keys are the
   * backstop; the checks here exist to say why in a sentence.
   */
  public void deleteBudget(Integer budgetId) throws EnvelopeException {
    requireAdmin();
    Budget budget = get(Budget.class, budgetId);
    if (budget == null)
      throw new EnvelopeException(EnvelopeException.Name.Invalid_Data, "there is no budget " + budgetId);

    long accounts = countRows(
        "select count(a) from Account a where a.budget.id = :id", "id", budgetId);
    if (accounts > 0)
      throw new EnvelopeException(EnvelopeException.Name.Invalid_Data,
          "\"" + budget.getName() + "\" still has " + accounts + (accounts == 1 ? " account" : " accounts"));
    long users = countRows(
        "select count(u) from User u where u.budget.id = :id", "id", budgetId);
    if (users > 0)
      throw new EnvelopeException(EnvelopeException.Name.Invalid_Data,
          "\"" + budget.getName() + "\" is still the budget of " + users + (users == 1 ? " user" : " users"));

    delete(budget);
    getCurrentSession().flush();   // see renameAccount
  }

  // ------------------------------------------------------------------------
  // Budget structure: accounts and the categories under them.
  //
  // The delete guards here need no locking, unlike deleteAllocation's.  What
  // stops a category disappearing out from under an allocation is the foreign
  // key itself -- allocations.category and categories.account are both ON DELETE
  // RESTRICT -- so the worst a lost race can do is fail the delete, which is
  // safe.  deleteAllocation had no such backstop: nothing in the database
  // prevents a transaction from reaching zero allocations, so that check had to
  // be made atomic by hand.  The checks below exist to give a usable message
  // rather than a raw constraint violation.
  // ------------------------------------------------------------------------

  /** Stands in for "no row to exclude" when checking a name for a new entity. */
  private static final Integer NO_ROW = Integer.valueOf(-1);

  /** Trim a user-supplied name and insist it is neither empty nor over-long. */
  private String checkName(String name) throws EnvelopeException {
    String trimmed = name == null ? "" : name.trim();
    if (trimmed.isEmpty())
      throw new EnvelopeException(EnvelopeException.Name.Invalid_Data, "a name is required");
    if (trimmed.length() > 64)
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data, "a name may be at most 64 characters");
    return trimmed;
  }

  /**
   * Refuse a name a sibling already holds, so the caller gets a sentence rather
   * than "Duplicate entry '0-Checking' for key 'budget_name'".  The unique index
   * is still the thing that actually guarantees it; this only reads better.
   *
   * @param excludeId the row being renamed, or -1 when creating a new one
   */
  private void checkAccountNameFree(Integer budgetId, String name, Integer excludeId)
      throws EnvelopeException {
    long taken = ((Number)getCurrentSession()
        .createSelectionQuery("select count(a) from Account a where a.budget.id = :budgetId"
                     + " and a.name = :name and a.id <> :excludeId", Long.class)
        .setParameter("budgetId", budgetId)
        .setParameter("name", name)
        .setParameter("excludeId", excludeId)
        .uniqueResult()).longValue();
    if (taken > 0)
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data,
          "this budget already has an account called \"" + name + "\"");
  }

  /** As checkAccountNameFree, one level down: categories are unique per account. */
  private void checkCategoryNameFree(Integer accountId, String name, Integer excludeId)
      throws EnvelopeException {
    long taken = ((Number)getCurrentSession()
        .createSelectionQuery("select count(c) from Category c where c.account.id = :accountId"
                     + " and c.name = :name and c.id <> :excludeId", Long.class)
        .setParameter("accountId", accountId)
        .setParameter("name", name)
        .setParameter("excludeId", excludeId)
        .uniqueResult()).longValue();
    if (taken > 0)
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data,
          "that account already has a category called \"" + name + "\"");
  }

  private long countRows(String hql, String parameter, Integer value) {
    return ((Number)getCurrentSession()
        .createSelectionQuery(hql, Long.class)
        .setParameter(parameter, value)
        .uniqueResult()).longValue();
  }

  /**
   * Remove one row from a preset.
   *
   * <p>No locking and no guard: a preset row has nothing depending on it, and a
   * preset with no rows left is simply a preset that no longer exists.  Inserting
   * and updating rows is savePreset.
   *
   * @param presetRowId the row to remove
   */
  public void deletePresetRow(Integer presetRowId) throws EnvelopeException {
    requireMyPresetRow(presetRowId);
    AllocationPreset row = get(AllocationPreset.class, presetRowId);
    if (row == null)
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data, "there is no preset row " + presetRowId);

    delete(row);
    getCurrentSession().flush();   // see renameAccount
  }

  /**
   * Remove a whole named preset from a budget, every row of it.
   *
   * @param budgetId the budget it belongs to
   * @param name     the preset name
   */
  public void deletePresetNamed(Integer budgetId, String name) throws EnvelopeException {
    requireMyBudget(budgetId);
    List<AllocationPreset> rows = getCurrentSession()
        .createSelectionQuery("from AllocationPreset p where p.budget.id = :budgetId and p.name = :name", AllocationPreset.class)
        .setParameter("budgetId", budgetId)
        .setParameter("name", name)
        .list();
    if (rows.isEmpty())
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data, "this budget has no preset called \"" + name + "\"");

    for (AllocationPreset row : rows) delete(row);
    getCurrentSession().flush();   // see renameAccount
  }

  /**
   * Rename a preset, which means renaming every row that carries the name.
   *
   * @param budgetId the budget it belongs to
   * @param oldName  the name now
   * @param newName  the name wanted
   */
  public void renamePresetNamed(Integer budgetId, String oldName, String newName)
      throws EnvelopeException {
    requireMyBudget(budgetId);
    String wanted = checkName(newName);

    List<AllocationPreset> rows = getCurrentSession()
        .createSelectionQuery("from AllocationPreset p where p.budget.id = :budgetId and p.name = :name", AllocationPreset.class)
        .setParameter("budgetId", budgetId)
        .setParameter("name", oldName)
        .list();
    if (rows.isEmpty())
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data, "this budget has no preset called \"" + oldName + "\"");

    for (AllocationPreset row : rows) row.setName(wanted);
    getCurrentSession().flush();   // see renameAccount
  }

  /**
   * Rename a budget.
   *
   * @param budgetId the budget to rename
   * @param name     its new name, unique across the installation
   */
  public void renameBudget(Integer budgetId, String name) throws EnvelopeException {
    requireAdmin();
    String budgetName = checkName(name);
    Budget budget = get(Budget.class, budgetId);
    if (budget == null)
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data, "there is no budget " + budgetId);

    // budgets.name carries a unique index of its own, with no budget to scope it
    long taken = ((Number)getCurrentSession()
        .createSelectionQuery("select count(b) from Budget b where b.name = :name and b.id <> :budgetId", Long.class)
        .setParameter("name", budgetName)
        .setParameter("budgetId", budgetId)
        .uniqueResult()).longValue();
    if (taken > 0)
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data, "there is already a budget called \"" + budgetName + "\"");

    budget.setName(budgetName);
    getCurrentSession().flush();   // see renameAccount
  }

  /**
   * Add an account to a budget.
   *
   * @param budgetId the budget it belongs to
   * @param name     the account name, unique within that budget
   * @param type     one of Account.AccountType -- Debit, Credit or Loan
   *
   * @return the new Account, detached
   */
  public Account createAccount(Integer budgetId, String name, String type)
      throws EnvelopeException {
    requireMyBudget(budgetId);
    String accountName = checkName(name);

    // get(), not load().  load() hands back an uninitialized proxy, which would
    // then travel to the client inside the returned entity as a proxy with no
    // session behind it -- usable only until something touched it, at which point
    // it throws LazyInitializationException.
    Budget budget = get(Budget.class, budgetId);
    if (budget == null)
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data, "there is no budget " + budgetId);

    Account.AccountType accountType;
    try {
      accountType = Account.AccountType.valueOf(type);
    } catch (IllegalArgumentException e) {
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data, "\"" + type + "\" is not an account type");
    }

    checkAccountNameFree(budgetId, accountName, NO_ROW);

    Account account = new Account();
    account.setBudget(budget);
    account.setName(accountName);
    account.setType(accountType);
    // both are NOT NULL with no default on the entity, so they have to be set
    account.setRate(BigDecimal.ZERO);
    account.setCeling(Money.ZERO);

    getCurrentSession().persist(account);
    getCurrentSession().flush();
    Hibernate.initialize(account);
    return evict(account);
  }

  /**
   * Rename an account.
   *
   * @param accountId the account to rename
   * @param name      its new name, unique within its budget
   */
  public void renameAccount(Integer accountId, String name) throws EnvelopeException {
    requireMyAccount(accountId);
    String accountName = checkName(name);
    Account account = load(Account.class, accountId);
    checkAccountNameFree(account.getBudget().getId(), accountName, accountId);

    account.setName(accountName);
    // Flush here rather than leaving it to Controller.  Controller flushes from a
    // finally block, where a constraint violation cannot reach its catch: the
    // response has already gone out saying the change worked, and the session is
    // left holding its locks.  Flushing inside the method keeps the failure where
    // it can be reported.
    getCurrentSession().flush();
  }

  /**
   * Remove an account, which must be empty.
   *
   * @param accountId the account to remove
   */
  public void deleteAccount(Integer accountId) throws EnvelopeException {
    requireMyAccount(accountId);
    Account account = load(Account.class, accountId);

    long categories = countRows(
        "select count(c) from Category c where c.account.id = :accountId", "accountId", accountId);
    if (categories > 0)
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data,
          "\"" + account.getName() + "\" still has " + categories
          + (categories == 1 ? " category" : " categories") + " in it");

    delete(account);
    getCurrentSession().flush();   // see renameAccount
  }

  /**
   * Add a category to an account.
   *
   * @param accountId the account it belongs to
   * @param name      the category name, unique within that account
   *
   * @return the new Category, detached
   */
  public Category createCategory(Integer accountId, String name) throws EnvelopeException {
    requireMyAccount(accountId);
    String categoryName = checkName(name);

    // get(), not load() -- see createAccount
    Account account = get(Account.class, accountId);
    if (account == null)
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data, "there is no account " + accountId);

    checkCategoryNameFree(accountId, categoryName, NO_ROW);

    Category category = new Category();
    category.setAccount(account);
    category.setName(categoryName);

    getCurrentSession().persist(category);
    getCurrentSession().flush();
    Hibernate.initialize(category);
    return evict(category);
  }

  /**
   * Rename a category.
   *
   * @param categoryId the category to rename
   * @param name       its new name, unique within its account
   */
  public void renameCategory(Integer categoryId, String name) throws EnvelopeException {
    requireMyCategory(categoryId);
    String categoryName = checkName(name);
    Category category = load(Category.class, categoryId);
    checkCategoryNameFree(category.getAccount().getId(), categoryName, categoryId);

    category.setName(categoryName);
    getCurrentSession().flush();   // see renameAccount
  }

  /**
   * Remove a category, which must have no allocations pointing at it.
   *
   * @param categoryId the category to remove
   */
  public void deleteCategory(Integer categoryId) throws EnvelopeException {
    requireMyCategory(categoryId);
    Category category = load(Category.class, categoryId);

    long allocations = countRows(
        "select count(a) from Allocation a where a.category.id = :categoryId",
        "categoryId", categoryId);
    if (allocations > 0)
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data,
          "\"" + category.getName() + "\" is used by " + allocations
          + (allocations == 1 ? " allocation" : " allocations"));

    delete(category);
    getCurrentSession().flush();   // see renameAccount
  }
}
