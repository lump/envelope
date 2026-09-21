package net.lump.envelope.client;

import net.lump.envelope.client.portal.HibernatePortal;
import net.lump.envelope.client.ui.components.Hierarchy;
import net.lump.envelope.shared.entity.*;
import net.lump.envelope.shared.exception.AbortException;
import net.lump.envelope.shared.exception.EnvelopeException;
import net.lump.lib.Money;
import org.apache.log4j.Logger;
import org.hibernate.criterion.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Creates detached criteria queries.
 *
 * @author Troy Bowman
 */
@SuppressWarnings({"unchecked"})
public class CriteriaFactory {
  private static CriteriaFactory singleton;
  private static Logger logger;

  private CriteriaFactory() {
    logger = Logger.getLogger(this.getClass());
  }

  public static CriteriaFactory getInstance() {
    if (singleton == null) singleton = new CriteriaFactory();
    return singleton;
  }

  public Budget getBudgetForUser(String user) throws AbortException {
    return (Budget)(new HibernatePortal()).detachedCriteriaQueryUnique(
        DetachedCriteria.forClass(User.class)
            .add(Restrictions.eq("name", user))
            .setProjection(Projections.property("budget")));
  }

  public List<String> getEntitiesforBudget(Budget budget)
      throws AbortException {
    List<String> list =
        (List<String>)(new HibernatePortal()).detachedCriteriaQueryList(
            DetachedCriteria.forClass(Transaction.class)
                .createAlias("allocations", "a")
                .createAlias("a.category", "c")
                .createAlias("c.account", "acc")
                .add(Restrictions.eq("acc.budget", budget))
                .add(Restrictions.not(Restrictions.eq("entity", "")))
                .setProjection(Projections.distinct(Projections.property("entity")))
                .addOrder(Order.asc("entity")));
    return list;
  }

  public DetachedCriteria getCategoriesforBudget(Budget budget)
      throws AbortException {
    return DetachedCriteria.forClass(Category.class)
        .createAlias("account", "a")
        .add(Restrictions.eq("a.budget", budget))
        .addOrder(Order.asc("name"));
  }

  /**
   * The accounts of a budget, each with the sum of its allocations.
   *
   * <p>The balance is derived from the allocations, but the *list* deliberately is
   * not: this used to be a single projection over Allocation, which inner-joins
   * its way to the account and so omitted any account that had no allocations yet.
   * A newly created account is still an account, and one invisible in the tree
   * cannot be given a category to make it visible.  So the structure comes from
   * the accounts table and the money is looked up against it, defaulting to zero.
   */
  public List<Hierarchy.AccountTotal> getAccountTotals(Budget budget)
      throws AbortException {
    HibernatePortal portal = new HibernatePortal();

    HashMap<Integer, Money> balances = new HashMap<Integer, Money>();
    List<Object[]> sums =
        (List<Object[]>)portal.detachedCriteriaQueryList(
            DetachedCriteria.forClass(Allocation.class)
                .createAlias("category", "c")
                .createAlias("c.account", "a")
                .add(Restrictions.eq("a.budget", budget))
                .setProjection(Projections.projectionList()
                    .add(Projections.groupProperty("a.id"))
                    .add(Projections.sum("amount"))));
    if (sums != null)
      for (Object[] o : sums)
        balances.put((Integer)o[0], o[1] == null ? Money.ZERO : (Money)o[1]);

    List<Hierarchy.AccountTotal> retval = new ArrayList<Hierarchy.AccountTotal>();
    List<Account> accounts =
        (List<Account>)portal.detachedCriteriaQueryList(
            DetachedCriteria.forClass(Account.class)
                .add(Restrictions.eq("budget", budget))
                .addOrder(Order.asc("name")));
    if (accounts != null)
      for (Account account : accounts) {
        Money balance = balances.get(account.getId());
        retval.add(new Hierarchy.AccountTotal(
            account, account.getName(), account.getId(),
            balance == null ? Money.ZERO : balance));
      }

    return retval;
  }

  /** Every allocation preset row in a budget, grouped by name when read in order. */
  public DetachedCriteria getPresetsForBudget(Budget budget) {
    return DetachedCriteria.forClass(AllocationPreset.class)
        .add(Restrictions.eq("budget", budget))
        .addOrder(Order.asc("name"));
  }

  public Transaction getTransactionById(Integer id) throws AbortException {
    return (Transaction)new HibernatePortal().detachedCriteriaQueryUnique(
        DetachedCriteria.forClass(Transaction.class)
        .add(Restrictions.eq("id", id)));
  }

  /**
   * The categories of an account, each with the sum of its allocations.
   *
   * <p>Same shape as getAccountTotals and for the same reason: a category with no
   * allocations yet has to appear, so the list comes from the categories table and
   * the money is looked up against it.
   */
  public List<Hierarchy.CategoryTotal> getCategoriesForAccount(Account account)
      throws AbortException {
    HibernatePortal portal = new HibernatePortal();

    HashMap<Integer, Money> balances = new HashMap<Integer, Money>();
    List<Object[]> sums =
        (List<Object[]>)portal.detachedCriteriaQueryList(
            DetachedCriteria.forClass(Allocation.class)
                .createAlias("category", "c")
                .add(Restrictions.eq("c.account", account))
                .setProjection(Projections.projectionList()
                    .add(Projections.groupProperty("c.id"))
                    .add(Projections.sum("amount"))));
    if (sums != null)
      for (Object[] o : sums)
        balances.put((Integer)o[0], o[1] == null ? Money.ZERO : (Money)o[1]);

    List<Hierarchy.CategoryTotal> retval = new ArrayList<Hierarchy.CategoryTotal>();
    List<Category> categories =
        (List<Category>)portal.detachedCriteriaQueryList(
            DetachedCriteria.forClass(Category.class)
                .add(Restrictions.eq("account", account))
                .addOrder(Order.asc("name")));
    if (categories != null)
      for (Category category : categories) {
        Money balance = balances.get(category.getId());
        retval.add(new Hierarchy.CategoryTotal(
            category.getName(), category.getId(),
            balance == null ? Money.ZERO : balance));
      }

    return retval;
  }

  public DetachedCriteria getBeginningBalance(Object thing,
      Date endDate,
      Boolean reconciled) {
    if (!(thing instanceof Hierarchy.CategoryTotal
        || thing instanceof Hierarchy.AccountTotal))
      throw new IllegalArgumentException(
          "first argument must be Cateogry or Budget");

    DetachedCriteria dc;
    if (thing instanceof Hierarchy.AccountTotal) {
      dc = DetachedCriteria.forClass(Transaction.class)
          .createAlias("allocations", "a")
          .createAlias("a.category", "c")
          .add(Restrictions.eq("c.account",
              ((Hierarchy.AccountTotal)thing).account))
          .add(Restrictions.lt("date", endDate));
      if (reconciled != null)
        dc.add(Restrictions.eq("reconciled", reconciled));
      dc.setProjection(Projections.sum("a.amount"));
    }
    else {
      dc = DetachedCriteria.forClass(Allocation.class)
          .createAlias("transaction", "t")
          .add(Restrictions.eq("category.id",
              ((Hierarchy.CategoryTotal)thing).id))
          .add(Restrictions.lt("t.date", endDate));
      if (reconciled != null)
        dc.add(Restrictions.eq("t.reconciled", reconciled));
      dc.setProjection(Projections.sum("amount"));
    }

    return dc;
  }

  public DetachedCriteria getBalance(Category c) {
    return DetachedCriteria.forClass(Allocation.class)
        .add(Restrictions.eq("category", c))
        .setProjection(Projections.sum("amount"));
  }

  public DetachedCriteria getBalances(List<Category> categories) {
    if (categories.size() == 0) throw new IllegalArgumentException("need one or more categories");
    return DetachedCriteria.forClass(Allocation.class)
        .add(Restrictions.in("category", categories))
        .setProjection(Projections.projectionList()
            .add(Projections.groupProperty("category"))
            .add(Projections.sum("amount")));
  }

  /**
   * The balance of every category in a budget as of a day, without one
   * transaction, keyed by category id.
   *
   * <p>The same sum the tree shows under each account, taken across the whole
   * budget in one query so the transaction form can put a balance beside every
   * allocation row -- but only up to the transaction's own day, that day
   * included, so an old transaction shows the balance it met rather than
   * today's, and less the transaction being edited, which is left out on the
   * server so that the form's own saves never move the number: the form adds
   * that transaction's rows back itself, as they stand on screen.  A category
   * with nothing to sum is simply absent; callers read that as zero.  This
   * replaced an unscoped version that summed every category of every budget on
   * the server.
   *
   * @param budget      the budget
   * @param transaction the id of the transaction to leave out, or null for none
   * @param asOf        the last day to count, in the wire form (midnight UTC of
   *                    the day, as Transaction.date is carried), or null for all
   */
  public Map<Integer, Money> getCategoryBalances(Budget budget, Integer transaction, Date asOf)
      throws AbortException {
    HashMap<Integer, Money> balances = new HashMap<Integer, Money>();
    DetachedCriteria dc = DetachedCriteria.forClass(Allocation.class)
        .createAlias("category", "c")
        .createAlias("c.account", "a")
        .createAlias("transaction", "t")
        .add(Restrictions.eq("a.budget", budget));
    if (transaction != null) dc.add(Restrictions.ne("t.id", transaction));
    if (asOf != null) dc.add(Restrictions.le("t.date", asOf));
    List<Object[]> sums =
        (List<Object[]>)new HibernatePortal().detachedCriteriaQueryList(
            dc.setProjection(Projections.projectionList()
                .add(Projections.groupProperty("c.id"))
                .add(Projections.sum("amount"))));
    if (sums != null)
      for (Object[] o : sums)
        balances.put((Integer)o[0], o[1] == null ? Money.ZERO : (Money)o[1]);
    return balances;
  }

  /**
   * One category's balance as of a day, without one transaction: the
   * single-row form of getCategoryBalances, for a row the user has just put a
   * category on.
   *
   * @param category    the category
   * @param transaction the id of the transaction to leave out, or null for none
   * @param asOf        the last day to count, as for getCategoryBalances
   * @return the sum, zero if there is nothing to sum
   */
  public Money getCategoryBalance(Category category, Integer transaction, Date asOf)
      throws AbortException {
    DetachedCriteria dc = DetachedCriteria.forClass(Allocation.class)
        .createAlias("transaction", "t")
        .add(Restrictions.eq("category", category));
    if (transaction != null) dc.add(Restrictions.ne("t.id", transaction));
    if (asOf != null) dc.add(Restrictions.le("t.date", asOf));
    Object sum = new HibernatePortal().detachedCriteriaQueryUnique(
        dc.setProjection(Projections.sum("amount")));
    return sum == null ? Money.ZERO : (Money)sum;
  }

  public DetachedCriteria getTransactions(Object thing,
      Date beginDate,
      Date endDate)
      throws EnvelopeException {
    if (!((thing instanceof Hierarchy.CategoryTotal)
        || (thing instanceof Hierarchy.AccountTotal)))
      throw new IllegalArgumentException(
          "first argument must be CategoryTotal or AccountTotal");

    DetachedCriteria dc;

    if (thing instanceof Hierarchy.AccountTotal) {
      ProjectionList plist = Projections.projectionList();
      plist.add(Projections.property("reconciled"))
          .add(Projections.property("date"))
          .add(Projections.sum("a.amount"))
          .add(Projections.property("entity"))
          .add(Projections.property("description"))
          .add(Projections.groupProperty("id"));
      dc = DetachedCriteria.forClass(Transaction.class)
          .createAlias("allocations", "a")
          .createAlias("a.category", "c")
          .add(Restrictions.eq("c.account",
              ((Hierarchy.AccountTotal)thing).account))
          .add(Restrictions.ge("date", beginDate))
          .add(Restrictions.le("date", endDate))
          .setProjection(plist)
          .addOrder(Order.asc("date"))
          .addOrder(Order.asc("stamp"));
    }
    else {
      ProjectionList plist = Projections.projectionList();
      // A row here is an ALLOCATION, not a transaction, and that is deliberate:
      // grouping by the allocation is what keeps an auto-deduct pair visible in the
      // category's history as the +X that arrived and the -X that left again.
      // Grouping by t.id would net them into a single 0.00 row and lose the payment
      // -- 4219 of the 4440 multi-allocation transaction/category pairs in this
      // budget are that shape.  The projection carries t.id separately so a row can
      // still be keyed to its transaction, and the trailing allocation id is what
      // tells two rows of one transaction apart.  Don't "simplify" this to t.id.
      plist.add(Projections.property("t.reconciled"))
          .add(Projections.property("t.date"))
          .add(Projections.sum("amount"))
          .add(Projections.property("t.entity"))
          .add(Projections.property("t.description"))
          .add(Projections.property("t.id"))
          .add(Projections.groupProperty("id"));
      dc = DetachedCriteria.forClass(Allocation.class)
          .createAlias("transaction", "t")
          .add(Restrictions.eq("category.id",
              ((Hierarchy.CategoryTotal)thing).id))
          .add(Restrictions.ge("t.date", beginDate))
          .add(Restrictions.le("t.date", endDate))
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
          .addOrder(Order.asc("stamp"));
    }

    return dc;
  }

}
