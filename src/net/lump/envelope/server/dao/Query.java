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
import org.hibernate.Criteria;
import org.hibernate.Hibernate;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.ProjectionList;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;

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
 * <p>The criteria API is Hibernate 5's, as before.  It no longer crosses the
 * wire, so moving these to HQL or JPA criteria is now an ordinary part of a
 * Hibernate upgrade rather than a protocol change.
 *
 * @author troy
 */
@SuppressWarnings("unchecked")
public class Query extends DAO {

  private Criteria criteria(Class<?> root) {
    return getCurrentSession().createCriteria(root);
  }

  /** The caller's budget, which the client's tree roots on. */
  public Budget myBudget() throws EnvelopeException {
    myBudgetId();   // refuses a user with none
    return getUser().getBudget();
  }

  /** The distinct payees in the caller's budget, for the entity field's completion. */
  public List<String> entities() throws EnvelopeException {
    return criteria(Transaction.class)
        .createAlias("allocations", "a")
        .createAlias("a.category", "c")
        .createAlias("c.account", "acc")
        .add(Restrictions.eq("acc.budget.id", myBudgetId()))
        .add(Restrictions.not(Restrictions.eq("entity", "")))
        .setProjection(Projections.distinct(Projections.property("entity")))
        .addOrder(Order.asc("entity"))
        .list();
  }

  /** Every category in the caller's budget, by name. */
  public List<Category> categories() throws EnvelopeException {
    return criteria(Category.class)
        .createAlias("account", "a")
        .add(Restrictions.eq("a.budget.id", myBudgetId()))
        .addOrder(Order.asc("name"))
        .list();
  }

  /** Every allocation preset row in the caller's budget, grouped by name when read in order. */
  public List<AllocationPreset> presets() throws EnvelopeException {
    return criteria(AllocationPreset.class)
        .add(Restrictions.eq("budget.id", myBudgetId()))
        .addOrder(Order.asc("name"))
        .list();
  }

  /**
   * The caller's accounts, each with the sum of its allocations.  The list comes
   * from the accounts table and the money is looked up against it, so an account
   * with no allocations yet still appears (see CLAUDE.md on the tree).
   */
  public List<Total<Account>> accountTotals() throws EnvelopeException {
    Integer budgetId = myBudgetId();

    HashMap<Integer, Money> balances = sums(criteria(Allocation.class)
        .createAlias("category", "c")
        .createAlias("c.account", "a")
        .add(Restrictions.eq("a.budget.id", budgetId))
        .setProjection(Projections.projectionList()
            .add(Projections.groupProperty("a.id"))
            .add(Projections.sum("amount"))));

    List<Total<Account>> totals = new ArrayList<Total<Account>>();
    for (Account account : (List<Account>)criteria(Account.class)
        .add(Restrictions.eq("budget.id", budgetId))
        .addOrder(Order.asc("name"))
        .list())
      totals.add(new Total<Account>(account, orZero(balances.get(account.getId()))));
    return totals;
  }

  /** One account's categories, each with the sum of its allocations; same shape as accountTotals. */
  public List<Total<Category>> categoryTotals(Integer accountId) throws EnvelopeException {
    requireMyAccount(accountId);

    HashMap<Integer, Money> balances = sums(criteria(Allocation.class)
        .createAlias("category", "c")
        .add(Restrictions.eq("c.account.id", accountId))
        .setProjection(Projections.projectionList()
            .add(Projections.groupProperty("c.id"))
            .add(Projections.sum("amount"))));

    List<Total<Category>> totals = new ArrayList<Total<Category>>();
    for (Category category : (List<Category>)criteria(Category.class)
        .add(Restrictions.eq("account.id", accountId))
        .addOrder(Order.asc("name"))
        .list())
      totals.add(new Total<Category>(category, orZero(balances.get(category.getId()))));
    return totals;
  }

  /** One transaction with its allocations, detached, for the form. */
  public Transaction transaction(Integer transactionId) throws EnvelopeException {
    requireMyTransaction(transactionId);
    Transaction transaction = load(Transaction.class, transactionId);
    Hibernate.initialize(transaction);
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
    Criteria c = criteria(Allocation.class)
        .createAlias("category", "c")
        .createAlias("c.account", "a")
        .createAlias("transaction", "t")
        .add(Restrictions.eq("a.budget.id", myBudgetId()));
    if (transactionId != null) c.add(Restrictions.ne("t.id", transactionId));
    if (asOf != null) c.add(Restrictions.le("t.date", asOf));
    return sums(c.setProjection(Projections.projectionList()
        .add(Projections.groupProperty("c.id"))
        .add(Projections.sum("amount"))));
  }

  /** One category's balance as of a day, without one transaction; zero if nothing to sum. */
  public Money categoryBalance(Integer categoryId, Integer transactionId, Date asOf)
      throws EnvelopeException {
    requireMyCategory(categoryId);
    Criteria c = criteria(Allocation.class)
        .createAlias("transaction", "t")
        .add(Restrictions.eq("category.id", categoryId));
    if (transactionId != null) c.add(Restrictions.ne("t.id", transactionId));
    if (asOf != null) c.add(Restrictions.le("t.date", asOf));
    return orZero((Money)c.setProjection(Projections.sum("amount")).uniqueResult());
  }

  /**
   * An account's balance before a day, optionally only reconciled transactions.
   *
   * @param reconciled true or false to count only those, null for all
   */
  public Money accountBeginningBalance(Integer accountId, Date before, Boolean reconciled)
      throws EnvelopeException {
    requireMyAccount(accountId);
    Criteria c = criteria(Transaction.class)
        .createAlias("allocations", "a")
        .createAlias("a.category", "c")
        .add(Restrictions.eq("c.account.id", accountId))
        .add(Restrictions.lt("date", before));
    if (reconciled != null) c.add(Restrictions.eq("reconciled", reconciled));
    return orZero((Money)c.setProjection(Projections.sum("a.amount")).uniqueResult());
  }

  /** A category's balance before a day, optionally only reconciled transactions. */
  public Money categoryBeginningBalance(Integer categoryId, Date before, Boolean reconciled)
      throws EnvelopeException {
    requireMyCategory(categoryId);
    Criteria c = criteria(Allocation.class)
        .createAlias("transaction", "t")
        .add(Restrictions.eq("category.id", categoryId))
        .add(Restrictions.lt("t.date", before));
    if (reconciled != null) c.add(Restrictions.eq("t.reconciled", reconciled));
    return orZero((Money)c.setProjection(Projections.sum("amount")).uniqueResult());
  }

  /**
   * An account's transactions between two days, one row per transaction:
   * {reconciled, date, net amount on this account, entity, description, id}.
   * Streamed, so the client can show progress over a long period.
   */
  public ScrollableResults accountTransactions(Integer accountId, Date begin, Date end)
      throws EnvelopeException {
    requireMyAccount(accountId);
    ProjectionList plist = Projections.projectionList()
        .add(Projections.property("reconciled"))
        .add(Projections.property("date"))
        .add(Projections.sum("a.amount"))
        .add(Projections.property("entity"))
        .add(Projections.property("description"))
        .add(Projections.groupProperty("id"));
    return criteria(Transaction.class)
        .createAlias("allocations", "a")
        .createAlias("a.category", "c")
        .add(Restrictions.eq("c.account.id", accountId))
        .add(Restrictions.ge("date", begin))
        .add(Restrictions.le("date", end))
        .setProjection(plist)
        .addOrder(Order.asc("date"))
        .addOrder(Order.asc("stamp"))
        .scroll();
  }

  /**
   * A category's allocations between two days, one row per ALLOCATION:
   * {reconciled, date, amount, entity, description, transaction id, allocation id}.
   */
  public ScrollableResults categoryTransactions(Integer categoryId, Date begin, Date end)
      throws EnvelopeException {
    requireMyCategory(categoryId);
    // A row here is an ALLOCATION, not a transaction, and that is deliberate:
    // grouping by the allocation is what keeps an auto-deduct pair visible in the
    // category's history as the +X that arrived and the -X that left again.
    // Grouping by t.id would net them into a single 0.00 row and lose the payment
    // -- 4219 of the 4440 multi-allocation transaction/category pairs in this
    // budget are that shape.  The projection carries t.id separately so a row can
    // still be keyed to its transaction, and the trailing allocation id is what
    // tells two rows of one transaction apart.  Don't "simplify" this to t.id.
    ProjectionList plist = Projections.projectionList()
        .add(Projections.property("t.reconciled"))
        .add(Projections.property("t.date"))
        .add(Projections.sum("amount"))
        .add(Projections.property("t.entity"))
        .add(Projections.property("t.description"))
        .add(Projections.property("t.id"))
        .add(Projections.groupProperty("id"));
    return criteria(Allocation.class)
        .createAlias("transaction", "t")
        .add(Restrictions.eq("category.id", categoryId))
        .add(Restrictions.ge("t.date", begin))
        .add(Restrictions.le("t.date", end))
        .setProjection(plist)
        // An unqualified "stamp" on an Allocation root is the ALLOCATION's version
        // stamp, so rows came back sequenced by when each allocation was last
        // saved and two transactions sharing a date had their rows interleaved --
        // on 2010-09-30 in category 53, transaction 4966's two amounts sat either
        // side of 4968's.  The transaction's stamp first keeps each transaction's
        // rows together; the allocation's then orders them within it.  t.stamp is
        // functionally dependent on the a.id group key through the join, so this
        // stays legal if ONLY_FULL_GROUP_BY is ever turned on.
        .addOrder(Order.asc("t.date"))
        .addOrder(Order.asc("t.stamp"))
        .addOrder(Order.asc("stamp"))
        .scroll();
  }

  // ------------------------------------------------------------------ helpers

  /** Read a {group id, sum} projection into a map, a null sum as zero. */
  private HashMap<Integer, Money> sums(Criteria grouped) {
    HashMap<Integer, Money> sums = new HashMap<Integer, Money>();
    for (Object[] row : (List<Object[]>)grouped.list())
      sums.put((Integer)row[0], orZero((Money)row[1]));
    return sums;
  }

  private static Money orZero(Money m) {
    return m == null ? Money.ZERO : m;
  }
}
