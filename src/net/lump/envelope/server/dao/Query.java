package net.lump.envelope.server.dao;

import net.lump.envelope.shared.entity.Account;
import net.lump.envelope.shared.entity.Allocation;
import net.lump.envelope.shared.entity.AllocationPreset;
import net.lump.envelope.shared.entity.Budget;
import net.lump.envelope.shared.entity.Category;
import net.lump.envelope.shared.entity.Transaction;
import net.lump.envelope.shared.exception.EnvelopeException;
import net.lump.envelope.shared.query.Total;
import net.lump.lib.Money;
import org.hibernate.Hibernate;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.query.SelectionQuery;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Every read the client makes, each a named command scoped to the caller's budget.
 *
 * <p>These used to be built on the client as {@code DetachedCriteria} and
 * Java-serialized to {@code Generic}, which ran whatever arrived.  That let any
 * logged-in user query any mapped entity -- {@code User} included, password hashes
 * and all -- and meant no read could ever be confined to a budget, since the
 * server did not know what a query was for.  The criteria themselves moved here
 * unchanged, so the SQL each one produces is what it was; what changed is that the
 * client now names a question and supplies ids and dates, and the budget is never
 * the client's to say: it comes from the authenticated user, and any id the client
 * names is checked against it first.
 *
 * <p>They were Hibernate 5 criteria until the move to Hibernate 7, which removed
 * that API; each is now the HQL for the same joins, filters, grouping and
 * ordering, and an old-versus-new diff of every read matched.
 *
 * @author troy
 */
public class Query extends DAO {

  /** The caller's budget, which the client's tree roots on. */
  public Budget myBudget() throws EnvelopeException {
    myBudgetId();   // refuses a user with none
    return getUser().getBudget();
  }

  /** The distinct payees in the caller's budget, for the entity field's completion. */
  public List<String> entities() throws EnvelopeException {
    return getCurrentSession().createSelectionQuery(
            "select distinct t.entity from Transaction t join t.allocations a"
            + " where a.category.account.budget.id = :budget and t.entity <> ''"
            + " order by t.entity", String.class)
        .setParameter("budget", myBudgetId())
        .list();
  }

  /** Every category in the caller's budget, by name. */
  public List<Category> categories() throws EnvelopeException {
    return getCurrentSession().createSelectionQuery(
            "from Category c where c.account.budget.id = :budget order by c.name", Category.class)
        .setParameter("budget", myBudgetId())
        .list();
  }

  /** Every allocation preset row in the caller's budget, grouped by name when read in order. */
  public List<AllocationPreset> presets() throws EnvelopeException {
    return getCurrentSession().createSelectionQuery(
            "from AllocationPreset p where p.budget.id = :budget order by p.name", AllocationPreset.class)
        .setParameter("budget", myBudgetId())
        .list();
  }

  /**
   * The caller's accounts, each with the sum of its allocations.  The list comes
   * from the accounts table and the money is looked up against it, so an account
   * with no allocations yet still appears (see CLAUDE.md on the tree).
   */
  public List<Total<Account>> accountTotals() throws EnvelopeException {
    Integer budgetId = myBudgetId();

    HashMap<Integer, Money> balances = sums(getCurrentSession().createSelectionQuery(
            "select a.category.account.id, sum(a.amount) from Allocation a"
            + " where a.category.account.budget.id = :budget group by a.category.account.id", Object[].class)
        .setParameter("budget", budgetId));

    List<Total<Account>> totals = new ArrayList<Total<Account>>();
    for (Account account : getCurrentSession().createSelectionQuery(
            "from Account a where a.budget.id = :budget order by a.name", Account.class)
        .setParameter("budget", budgetId)
        .list())
      totals.add(new Total<Account>(account, orZero(balances.get(account.getId()))));
    return totals;
  }

  /** One account's categories, each with the sum of its allocations; same shape as accountTotals. */
  public List<Total<Category>> categoryTotals(Integer accountId) throws EnvelopeException {
    requireMyAccount(accountId);

    HashMap<Integer, Money> balances = sums(getCurrentSession().createSelectionQuery(
            "select a.category.id, sum(a.amount) from Allocation a"
            + " where a.category.account.id = :account group by a.category.id", Object[].class)
        .setParameter("account", accountId));

    List<Total<Category>> totals = new ArrayList<Total<Category>>();
    for (Category category : getCurrentSession().createSelectionQuery(
            "from Category c where c.account.id = :account order by c.name", Category.class)
        .setParameter("account", accountId)
        .list())
      totals.add(new Total<Category>(category, orZero(balances.get(category.getId()))));
    return totals;
  }

  /** One transaction with its allocations, detached, for the form. */
  public Transaction transaction(Integer transactionId) throws EnvelopeException {
    requireMyTransaction(transactionId);
    Transaction transaction = get(Transaction.class, transactionId);
    Hibernate.initialize(transaction.getAllocations());
    return evict(transaction);
  }

  /**
   * The balance of every category in the caller's budget as of a day, without one
   * transaction, keyed by category id.  See CLAUDE.md on the allocation table's
   * Balance column for why the day is inclusive and the transaction left out.
   *
   * @param transactionId the transaction to leave out, or null for none.  Not
   *                      checked for ownership: it only ever removes rows from a
   *                      sum that is already confined to the caller's budget.
   * @param asOf          the last day to count, in the wire form, or null for all
   */
  public HashMap<Integer, Money> categoryBalances(Integer transactionId, Date asOf)
      throws EnvelopeException {
    SelectionQuery<Object[]> q = getCurrentSession().createSelectionQuery(
        "select a.category.id, sum(a.amount) from Allocation a"
        + " where a.category.account.budget.id = :budget"
        + (transactionId != null ? " and a.transaction.id <> :transaction" : "")
        + (asOf != null ? " and a.transaction.date <= :asOf" : "")
        + " group by a.category.id", Object[].class)
        .setParameter("budget", myBudgetId());
    if (transactionId != null) q.setParameter("transaction", transactionId);
    if (asOf != null) q.setParameter("asOf", day(asOf));
    return sums(q);
  }

  /** One category's balance as of a day, without one transaction; zero if nothing to sum. */
  public Money categoryBalance(Integer categoryId, Integer transactionId, Date asOf)
      throws EnvelopeException {
    requireMyCategory(categoryId);
    SelectionQuery<Object> q = getCurrentSession().createSelectionQuery(
        "select sum(a.amount) from Allocation a where a.category.id = :category"
        + (transactionId != null ? " and a.transaction.id <> :transaction" : "")
        + (asOf != null ? " and a.transaction.date <= :asOf" : ""), Object.class)
        .setParameter("category", categoryId);
    if (transactionId != null) q.setParameter("transaction", transactionId);
    if (asOf != null) q.setParameter("asOf", day(asOf));
    return orZero(money(q.uniqueResult()));
  }

  /**
   * An account's balance before a day, optionally only reconciled transactions.
   *
   * @param reconciled true or false to count only those, null for all
   */
  public Money accountBeginningBalance(Integer accountId, Date before, Boolean reconciled)
      throws EnvelopeException {
    requireMyAccount(accountId);
    return beginningBalance("a.category.account.id", accountId, before, reconciled);
  }

  /** A category's balance before a day, optionally only reconciled transactions. */
  public Money categoryBeginningBalance(Integer categoryId, Date before, Boolean reconciled)
      throws EnvelopeException {
    requireMyCategory(categoryId);
    return beginningBalance("a.category.id", categoryId, before, reconciled);
  }

  /** @param path a fixed property path chosen by this class, never by the caller */
  private Money beginningBalance(String path, Integer id, Date before, Boolean reconciled) {
    SelectionQuery<Object> q = getCurrentSession().createSelectionQuery(
        "select sum(a.amount) from Allocation a where " + path + " = :id"
        + " and a.transaction.date < :before"
        + (reconciled != null ? " and a.transaction.reconciled = :reconciled" : ""), Object.class)
        .setParameter("id", id)
        .setParameter("before", before == null ? null : day(before));
    if (reconciled != null) q.setParameter("reconciled", reconciled);
    return orZero(money(q.uniqueResult()));
  }

  /**
   * An account's transactions between two days, one row per transaction:
   * {reconciled, date, net amount on this account, entity, description, id}.
   * Streamed, so the client can show progress over a long period.
   */
  public ScrollableResults<Object[]> accountTransactions(Integer accountId, Date begin, Date end)
      throws EnvelopeException {
    requireMyAccount(accountId);
    return rows(getCurrentSession().createSelectionQuery(
            "select t.reconciled, t.date, sum(a.amount), t.entity, t.description, t.id"
            + " from Transaction t join t.allocations a"
            + " where a.category.account.id = :account and t.date >= :begin and t.date <= :end"
            + " group by t.id order by t.date, t.stamp", Object[].class)
        .setParameter("account", accountId)
        .setParameter("begin", day(begin))
        .setParameter("end", day(end)));
  }

  /**
   * A category's allocations between two days, one row per ALLOCATION:
   * {reconciled, date, amount, entity, description, transaction id, allocation id}.
   */
  public ScrollableResults<Object[]> categoryTransactions(Integer categoryId, Date begin, Date end)
      throws EnvelopeException {
    requireMyCategory(categoryId);
    // A row here is an ALLOCATION, not a transaction, and that is deliberate:
    // grouping by the allocation is what keeps an auto-deduct pair visible in the
    // category's history as the +X that arrived and the -X that left again.
    // Grouping by t.id would net them into a single 0.00 row and lose the payment
    // -- 4219 of the 4440 multi-allocation transaction/category pairs in this
    // budget are that shape.  The row carries t.id separately so it can still be
    // keyed to its transaction, and the trailing allocation id is what tells two
    // rows of one transaction apart.  Don't "simplify" this to t.id.
    //
    // The order: an unqualified "stamp" here would be the ALLOCATION's version
    // stamp, which sequenced rows by when each allocation was last saved and
    // interleaved two transactions sharing a date -- on 2010-09-30 in category 53,
    // transaction 4966's two amounts sat either side of 4968's.  The transaction's
    // stamp first keeps each transaction's rows together; the allocation's then
    // orders them within it.  t.stamp is functionally dependent on the a.id group
    // key through the join, so this stays legal if ONLY_FULL_GROUP_BY is turned on.
    return rows(getCurrentSession().createSelectionQuery(
            "select t.reconciled, t.date, sum(a.amount), t.entity, t.description, t.id, a.id"
            + " from Allocation a join a.transaction t"
            + " where a.category.id = :category and t.date >= :begin and t.date <= :end"
            + " group by a.id order by t.date, t.stamp, a.stamp", Object[].class)
        .setParameter("category", categoryId)
        .setParameter("begin", day(begin))
        .setParameter("end", day(end)));
  }

  // ------------------------------------------------------------------ helpers

  /**
   * Stream rows with their amount (column 2) as Money, which is what the client's
   * table casts it to.  Insensitive, because Controller counts the rows by
   * scrolling to the end and back before it sends them.
   */
  private ScrollableResults<Object[]> rows(SelectionQuery<Object[]> q) {
    return q.setTupleTransformer((tuple, aliases) -> {
          tuple[2] = money(tuple[2]);
          return tuple;
        })
        .scroll(ScrollMode.SCROLL_INSENSITIVE);
  }

  /** Read a {group id, sum} query into a map, a null sum as zero. */
  private HashMap<Integer, Money> sums(SelectionQuery<Object[]> grouped) {
    HashMap<Integer, Money> sums = new HashMap<Integer, Money>();
    for (Object[] row : grouped.list())
      sums.put((Integer)row[0], orZero(money(row[1])));
    return sums;
  }

  /**
   * A sum over a Money column as Money.  Whether Hibernate applies the column's
   * converter to an aggregate is its business and has changed between versions,
   * so take either.
   */
  private static Money money(Object sum) {
    if (sum == null || sum instanceof Money) return (Money)sum;
    if (sum instanceof BigDecimal) return new Money((BigDecimal)sum);
    return new Money(new BigDecimal(sum.toString()));
  }

  /**
   * A day bound as the DATE the column holds.  The criteria API bound a
   * java.util.Date against a DATE property as a DATE implicitly; HQL infers the
   * parameter's type from the comparison and wants the property's own type.
   */
  private static java.sql.Date day(Date d) {
    return d instanceof java.sql.Date ? (java.sql.Date)d : new java.sql.Date(d.getTime());
  }

  private static Money orZero(Money m) {
    return m == null ? Money.ZERO : m;
  }
}
