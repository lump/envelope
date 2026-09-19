package net.lump.envelope.server.dao;

import net.lump.envelope.shared.entity.Account;
import net.lump.envelope.shared.entity.Allocation;
import net.lump.envelope.shared.entity.AllocationPreset;
import net.lump.envelope.shared.entity.Budget;
import net.lump.envelope.shared.entity.Category;
import net.lump.envelope.shared.entity.Transaction;
import net.lump.envelope.shared.entity.User;
import net.lump.envelope.shared.command.security.Permission;
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
   * Sets reconciled
   * @param transactionID the ID of the transaction
   * @param reconciled whether this is reconciled or not
   */
  public void updateReconciled(Integer transactionID, Boolean reconciled) {
    Transaction t = load(Transaction.class, transactionID);
    t.setReconciled(reconciled);
  }

  /**
   * Delete one Allocation.
   *
   * <p>Deliberately narrow rather than a generic "delete this entity" command.
   * Nothing on the server checks whose budget a command touches, so a generic
   * delete verb would hand every authenticated caller the ability to remove any
   * row of any type; this one can only ever remove an allocation, and it enforces
   * the invariant below no matter what the client believes.
   *
   * @param allocationId the id of the Allocation to remove
   *
   * @throws EnvelopeException if this is the transaction's last allocation
   */
  public void deleteAllocation(Integer allocationId) throws EnvelopeException {
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
    @SuppressWarnings("unchecked")
    List<Allocation> siblings = getCurrentSession()
        .createQuery("from Allocation a where a.transaction.id = :transactionId")
        .setParameter("transactionId", transactionId)
        .setLockMode("a", LockMode.PESSIMISTIC_WRITE)
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

    getCurrentSession().save(transaction);
    getCurrentSession().save(allocation);
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
    Transaction transaction = load(Transaction.class, transactionId);

    @SuppressWarnings("unchecked")
    List<Allocation> allocations = getCurrentSession()
        .createQuery("from Allocation a where a.transaction.id = :transactionId")
        .setParameter("transactionId", transactionId)
        .setLockMode("a", LockMode.PESSIMISTIC_WRITE)
        .list();

    for (Allocation allocation : allocations) delete(allocation);
    getCurrentSession().flush();

    delete(transaction);
  }

  private static final BigDecimal ONE_HUNDRED = new BigDecimal(100);

  /**
   * What one preset row is worth against a gross amount.
   *
   * <p>A percentage is stored as a whole number -- 7.60764626375748 means 7.6% --
   * and deliberately carried to many places, so the product is taken at full
   * precision and only the result is brought to the cent.  Money is only ever
   * pennies; nothing here goes near a float.
   */
  private Money valueOf(AllocationPreset preset, Money gross) {
    if (preset.getAllocationType() == AllocationPreset.AllocationType.fixed)
      return new Money(preset.getAllocation().setScale(2, RoundingMode.HALF_UP));

    return new Money(gross.toBigDecimal()
        .multiply(preset.getAllocation())
        .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP));
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
    getCurrentSession().saveOrUpdate(allocation);
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
    Transaction transaction = load(Transaction.class, transactionId);

    @SuppressWarnings("unchecked")
    List<Allocation> existing = getCurrentSession()
        .createQuery("from Allocation a where a.transaction.id = :transactionId")
        .setParameter("transactionId", transactionId)
        .setLockMode("a", LockMode.PESSIMISTIC_WRITE)
        .list();

    // a transaction reaches its budget only through its allocations, so that is
    // the only place the budget can be read from
    if (existing.isEmpty())
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data,
          "transaction " + transactionId + " has no allocations to take a budget from");
    Budget budget = existing.get(0).getCategory().getAccount().getBudget();

    @SuppressWarnings("unchecked")
    List<AllocationPreset> rows = getCurrentSession()
        .createQuery("from AllocationPreset p where p.budget.id = :budgetId and p.name = :name")
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
      Money value = valueOf(preset, gross == null ? Money.ZERO : gross);
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
        .createQuery("from User u order by u.name").list();
    List<User> out = new ArrayList<User>(users.size());
    for (User u : users) out.add(forClient(u));
    return out;
  }

  /** Every budget.  ADMIN only. */
  @SuppressWarnings("unchecked")
  public List<Budget> listBudgets() throws EnvelopeException {
    requireAdmin();
    List<Budget> budgets = getCurrentSession()
        .createQuery("from Budget b order by b.name").list();
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
   * @param cryptPassword the md5-crypt hash of the initial password, made by the
   *                      client the same way the login exchange makes one
   */
  public User createUser(String name, String realName, Integer budgetId,
                         Long permissions, String cryptPassword) throws EnvelopeException {
    requireAdmin();
    String userName = checkName(name);

    long taken = ((Number)getCurrentSession()
        .createQuery("select count(u) from User u where u.name = :name")
        .setParameter("name", userName).uniqueResult()).longValue();
    if (taken > 0)
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data, "there is already a user called \"" + userName + "\"");

    Budget budget = get(Budget.class, budgetId);
    if (budget == null)
      throw new EnvelopeException(EnvelopeException.Name.Invalid_Data, "there is no budget " + budgetId);
    if (cryptPassword == null || cryptPassword.isEmpty())
      throw new EnvelopeException(EnvelopeException.Name.Invalid_Data, "a password is required");

    User user = new User();
    user.setName(userName);
    user.setRealName(realName == null ? "" : realName.trim());
    user.setBudget(budget);
    user.setPermission(new Permission(permissions == null ? 0L : permissions));
    user.setCryptPassword(cryptPassword);

    getCurrentSession().save(user);
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
   * @param cryptPassword the md5-crypt hash, made by the client
   */
  public void setPassword(Integer userId, String cryptPassword) throws EnvelopeException {
    User caller = getUser();
    boolean self = caller.getId().equals(userId);
    if (!self && !caller.getPermission().hasPermission(Permission.ADMIN))
      throw new EnvelopeException(
          EnvelopeException.Name.Permission_Denied, "only an administrator can set another user's password");
    if (cryptPassword == null || cryptPassword.isEmpty())
      throw new EnvelopeException(EnvelopeException.Name.Invalid_Data, "a password is required");

    User user = self ? caller : get(User.class, userId);
    if (user == null)
      throw new EnvelopeException(EnvelopeException.Name.Invalid_Data, "there is no user " + userId);

    user.setCryptPassword(cryptPassword);
    getCurrentSession().flush();   // see renameAccount
  }

  /** Add a budget.  ADMIN only. */
  public Budget createBudget(String name) throws EnvelopeException {
    requireAdmin();
    String budgetName = checkName(name);

    long taken = ((Number)getCurrentSession()
        .createQuery("select count(b) from Budget b where b.name = :name")
        .setParameter("name", budgetName).uniqueResult()).longValue();
    if (taken > 0)
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data, "there is already a budget called \"" + budgetName + "\"");

    Budget budget = new Budget();
    budget.setName(budgetName);
    getCurrentSession().save(budget);
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
        .createQuery("select count(a) from Account a where a.budget.id = :budgetId"
                     + " and a.name = :name and a.id <> :excludeId")
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
        .createQuery("select count(c) from Category c where c.account.id = :accountId"
                     + " and c.name = :name and c.id <> :excludeId")
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
        .createQuery(hql)
        .setParameter(parameter, value)
        .uniqueResult()).longValue();
  }

  /**
   * Remove one row from a preset.
   *
   * <p>No locking and no guard: a preset row has nothing depending on it, and a
   * preset with no rows left is simply a preset that no longer exists.  Inserting
   * and updating rows needs no command of its own -- AllocationPreset is an
   * Identifiable, so the generic saveOrUpdate carries it.
   *
   * @param presetRowId the row to remove
   */
  public void deletePresetRow(Integer presetRowId) throws EnvelopeException {
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
    @SuppressWarnings("unchecked")
    List<AllocationPreset> rows = getCurrentSession()
        .createQuery("from AllocationPreset p where p.budget.id = :budgetId and p.name = :name")
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
    String wanted = checkName(newName);

    @SuppressWarnings("unchecked")
    List<AllocationPreset> rows = getCurrentSession()
        .createQuery("from AllocationPreset p where p.budget.id = :budgetId and p.name = :name")
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
        .createQuery("select count(b) from Budget b where b.name = :name and b.id <> :budgetId")
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

    getCurrentSession().save(account);
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

    getCurrentSession().save(category);
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
