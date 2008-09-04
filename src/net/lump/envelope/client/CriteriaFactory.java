package us.lump.envelope.client;

import org.apache.log4j.Logger;
import org.hibernate.FetchMode;
import org.hibernate.criterion.*;
import us.lump.envelope.client.portal.HibernatePortal;
import us.lump.envelope.entity.*;
import us.lump.envelope.exception.EnvelopeException;
import us.lump.lib.Money;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Creates detached criteria queries.
 *
 * @author Troy Bowman
 * @version $Id: CriteriaFactory.java,v 1.11 2008/09/04 00:57:27 troy Exp $
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

  public Budget getBudgetForUser(String user) {
    Budget retval = null;
    List l = null;
    try {
      l = (new HibernatePortal()).detachedCriteriaQuery(
          DetachedCriteria.forClass(User.class)
              .add(Restrictions.eq("name", user))
              .setProjection(Projections.property("budget")));
      if (l.size() > 0) retval = (Budget)l.get(0);
    } catch (EnvelopeException e) {
      logger.error(e);
    }
    return retval;
  }

  public List<Account> getAccountsForBudget(Budget budget) {
    List<Account> retval = new ArrayList<Account>();
    try {
      retval = (List<Account>)(new HibernatePortal()).detachedCriteriaQuery(
          DetachedCriteria.forClass(Account.class)
              .setFetchMode("categories", FetchMode.JOIN)
              .setFetchMode("budget", FetchMode.JOIN)
              .add(Restrictions.eq("budget", budget)));
    } catch (EnvelopeException e) {
      logger.error(e);
    }
    return retval;
  }

  public static class CategoryTotal {
    public Category category;
    public Money total;

    CategoryTotal(Category category, Money total) {
      this.category = category;
      this.total = total;
    }

    public String toString() { return category.toString(); }
  }

  public List<CategoryTotal> getCategoriesForAccount(Account account) {
    List<CategoryTotal> retval = new ArrayList<CategoryTotal>();
    try {
      ProjectionList plist = Projections.projectionList()
          .add(Projections.groupProperty("category"))
          .add(Projections.sum("amount"));
      for (Object[] o :
          (List<Object[]>)(new HibernatePortal()).detachedCriteriaQuery(
              DetachedCriteria.forClass(Allocation.class)
                  .setFetchMode("category", FetchMode.JOIN)
                  .createAlias("category", "c")
                  .setFetchMode("account", FetchMode.JOIN)
                  .add(Restrictions.eq("c.account", account))
                  .setProjection(plist)
                  .addOrder(Order.asc("category")))) {
        retval.add(new CategoryTotal((Category)o[0], (Money)o[1]));
      }
    } catch (EnvelopeException e) {
      logger.error(e);
    }
    return retval;
  }

  public Money getBeginningBalance(Identifiable categoryOrAccount,
                                   Date endDate,
                                   Boolean reconciled) {
    Money retval = null;
    if (!(categoryOrAccount instanceof Category
          || categoryOrAccount instanceof Account))
      throw new IllegalArgumentException(
          "first argument must be Cateogry or Budget");

    try {
      DetachedCriteria dc;
      if (categoryOrAccount instanceof Category) {
        dc = DetachedCriteria.forClass(Allocation.class)
            .createAlias("transaction", "t")
            .add(Restrictions.eq("category", categoryOrAccount))
            .add(Restrictions.lt("t.date", endDate));
        if (reconciled != null)
          dc.add(Restrictions.eq("t.reconciled", reconciled));
        dc.setProjection(Projections.sum("amount"));
      } else {
        dc = DetachedCriteria.forClass(Transaction.class)
            .createAlias("allocations", "a")
            .createAlias("a.category", "c")
            .add(Restrictions.eq("c.account", categoryOrAccount))
            .add(Restrictions.lt("date", endDate));
        if (reconciled != null)
          dc.add(Restrictions.eq("reconciled", reconciled));
        dc.setProjection(Projections.sum("a.amount"));
      }

      List l = (new HibernatePortal()).detachedCriteriaQuery(dc);
      retval = l != null && l.size() > 0 ? (Money)l.get(0) : new Money(0);


    } catch (EnvelopeException e) {
      logger.error(e);
    }
    return retval == null ? new Money(0) : retval;
  }

  public List<Object[]> getTransactions(Identifiable categoryOrAccount,
                                        Date beginDate,
                                        Date endDate) {
    if (!(categoryOrAccount instanceof Category
          || categoryOrAccount instanceof Account))
      throw new IllegalArgumentException(
          "first argument must be Category or Account");

    List<Object[]> retval = new ArrayList<Object[]>();
    try {

      if (categoryOrAccount instanceof Account) {
        ProjectionList plist = Projections.projectionList();
        plist.add(Projections.property("reconciled"))
            .add(Projections.property("date"))
            .add(Projections.sum("a.amount"))
            .add(Projections.property("entity"))
            .add(Projections.property("description"))
            .add(Projections.groupProperty("id"));
        retval =
            (new HibernatePortal()).detachedCriteriaQuery(
                DetachedCriteria.forClass(Transaction.class)
                    .createAlias("allocations", "a")
                    .createAlias("a.category", "c")
                    .add(Restrictions.eq("c.account", categoryOrAccount))
                    .add(Restrictions.ge("date", beginDate))
                    .add(Restrictions.le("date", endDate))
                    .setProjection(plist)
                    .addOrder(Order.asc("date"))
                    .addOrder(Order.asc("stamp"))
            );
      } else {
        ProjectionList plist = Projections.projectionList();
        plist.add(Projections.property("t.reconciled"))
            .add(Projections.property("t.date"))
            .add(Projections.sum("amount"))
            .add(Projections.property("t.entity"))
            .add(Projections.property("t.description"))
            .add(Projections.property("t.id"))
            .add(Projections.groupProperty("id"));
        retval =
            (new HibernatePortal()).detachedCriteriaQuery(
                DetachedCriteria.forClass(Allocation.class)
                    .createAlias("transaction", "t")
                    .add(Restrictions.eq("category", categoryOrAccount))
                    .add(Restrictions.ge("t.date", beginDate))
                    .add(Restrictions.le("t.date", endDate))
                    .setProjection(plist)
                    .addOrder(Order.asc("t.date"))
                    .addOrder(Order.asc("stamp"))
            );
      }
    } catch (EnvelopeException e) {
      logger.error(e);
    }
    return retval;
  }
}
