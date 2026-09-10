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

/**
 * Creates detached criteria queries.
 *
 * @author Troy Bowman
 * @version $Id: CriteriaFactory.java,v 1.24 2009/10/02 22:06:23 troy Exp $
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

  public DetachedCriteria getAllBalances() {
    return DetachedCriteria.forClass(Allocation.class)
        .setProjection(Projections.projectionList()
            .add(Projections.groupProperty("category"))
            .add(Projections.sum("amount")));
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
          .addOrder(Order.asc("t.date"))
          .addOrder(Order.asc("stamp"));
    }

    return dc;
  }

}
