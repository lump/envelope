package us.lump.envelope.client;

import org.apache.log4j.Logger;
import org.hibernate.criterion.*;
import us.lump.envelope.client.portal.HibernatePortal;
import us.lump.envelope.client.ui.components.Hierarchy;
import us.lump.envelope.entity.*;
import us.lump.envelope.exception.AbortException;
import us.lump.envelope.exception.EnvelopeException;
import us.lump.lib.Money;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Creates detached criteria queries.
 *
 * @author Troy Bowman
 * @version $Id: CriteriaFactory.java,v 1.22 2009/07/06 21:45:29 troy Exp $
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
                .setProjection(Projections.distinct(Projections.property(
                    "entity")))
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

  public List<Hierarchy.AccountTotal> getAccountTotals(Budget budget)
      throws AbortException {
    List<Hierarchy.AccountTotal> retval =
        new ArrayList<Hierarchy.AccountTotal>();

    ProjectionList plist = Projections.projectionList()
        .add(Projections.property("c.account"))
        .add(Projections.property("a.name").as("AccountName"))
        .add(Projections.groupProperty("a.id"))
        .add(Projections.sum("amount"));
//        .add(Projections.max("t.date"));
    List<Object[]> list =
        (List<Object[]>)(new HibernatePortal()).detachedCriteriaQueryList(
            DetachedCriteria.forClass(Allocation.class)
                .createAlias("category", "c")
                .createAlias("c.account", "a")
                .add(Restrictions.eq("a.budget", budget))
                .setProjection(plist)
//                .createAlias("transaction","t")
                .addOrder(Order.asc("AccountName")));
    if (list != null)
      for (Object[] o : list) {
        retval.add(new Hierarchy.AccountTotal(
            (Account)o[0], (String)o[1], (Integer)o[2], (Money)o[3]));
      }

    return retval;
  }

  public List<Hierarchy.CategoryTotal> getCategoriesForAccount(Account account)
      throws AbortException {
    List<Hierarchy.CategoryTotal> retval =
        new ArrayList<Hierarchy.CategoryTotal>();
    ProjectionList plist = Projections.projectionList()
        .add(Projections.property("c.name").as("CategoryName"))
        .add(Projections.groupProperty("c.id"))
        .add(Projections.sum("amount"))
        .add(Projections.max("t.date"));
    List<Object[]> list =
        (List<Object[]>)(new HibernatePortal()).detachedCriteriaQueryList(
            DetachedCriteria.forClass(Allocation.class)
                .createAlias("category", "c")
                .add(Restrictions.eq("c.account", account))
                .createAlias("transaction", "t")
                .setProjection(plist)
                .addOrder(Order.asc("CategoryName")));
    if (list != null)
      for (Object[] o : list)
        retval.add(new Hierarchy.CategoryTotal((String)o[0], (Integer)o[1], (Money)o[2]));

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
