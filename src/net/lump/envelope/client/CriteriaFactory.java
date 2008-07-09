package us.lump.envelope.client;

import org.hibernate.criterion.*;
import org.apache.log4j.Logger;
import us.lump.envelope.entity.*;
import us.lump.envelope.client.portal.HibernatePortal;
import us.lump.envelope.exception.EnvelopeException;
import us.lump.lib.Money;

import java.util.List;
import java.util.ArrayList;
import java.util.Date;

/**
 * Creates detached criteria queries.
 *
 * @author Troy Bowman
 * @version $Id: CriteriaFactory.java,v 1.5 2008/07/09 08:16:40 troy Exp $
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
              .add(Restrictions.eq("budget", budget)));
    } catch (EnvelopeException e) {
      logger.error(e);
    }
    return retval;
  }

  public List<Category> getCategoriesForBudget(Budget budget) {
    List<Category> retval = new ArrayList<Category>();
    try {
      retval = (List<Category>)(new HibernatePortal()).detachedCriteriaQuery(
          DetachedCriteria.forClass(Category.class)
              .createCriteria("account", "a")
              .add(Restrictions.eq("a.budget", budget)));
    } catch (EnvelopeException e) {
      logger.error(e);
    }
    return retval;
  }

  public ArrayList<Object[]> getAllocations(Category category,
                                            Date beginDate,
                                            Date endDate) {

    ArrayList<Object[]> retval = new ArrayList<Object[]>();
    try {
      ProjectionList plist = Projections.projectionList();
      plist.add(Projections.property("t.reconciled"))
          .add(Projections.property("t.date"))
          .add(Projections.sum("amount"))
          .add(Projections.property("t.entity"))
          .add(Projections.property("t.description"))
          .add(Projections.groupProperty("id"));
      retval = (ArrayList<Object[]>)(new HibernatePortal()).detachedCriteriaQuery(
          DetachedCriteria.forClass(Allocation.class)
              .createAlias("transaction", "t")
              .add(Restrictions.eq("category", category))
              .add(Restrictions.ge("t.date", beginDate))
              .add(Restrictions.le("t.date", endDate))
              .setProjection(plist)
              .addOrder(Order.asc("t.date"))
              .addOrder(Order.asc("stamp"))
          );
    } catch (EnvelopeException e) {
      logger.error(e);
    }
    return retval;
  }


  public Money getBeginningBalance(Category category, Date endDate, Boolean reconciled) {
    Money retval = null;

    try {
      DetachedCriteria dc = DetachedCriteria.forClass(Allocation.class)
          .createAlias("transaction", "t")
          .add(Restrictions.eq("category", category))
          .add(Restrictions.lt("t.date", endDate));
      if (reconciled != null)
        dc.add(Restrictions.eq("t.reconciled", reconciled));
      dc.setProjection(Projections.sum("amount"));

      retval = (Money)(new HibernatePortal()).detachedCriteriaQuery(dc).get(0);
    } catch (EnvelopeException e) {
      logger.error(e);
    }
    return retval == null ? new Money(0) : retval;
  }

  public Money getBeginningBalance(Account account, Date endDate, Boolean reconciled) {
    Money retval = null;

    try {
      DetachedCriteria dc = DetachedCriteria.forClass(Transaction.class)
          .createAlias("allocations", "a")
          .createAlias("a.category", "c")
          .add(Restrictions.eq("c.account", account))
          .add(Restrictions.lt("date", endDate));
      if (reconciled != null)
        dc.add(Restrictions.eq("reconciled", reconciled));
      dc.setProjection(Projections.sum("a.amount"));

      retval = (Money)(new HibernatePortal()).detachedCriteriaQuery(dc).get(0);
    } catch (EnvelopeException e) {
      logger.error(e);
    }
    return retval == null ? new Money(0) : retval;
  }

  public ArrayList<Object[]> getTransactions(Account account,
                                                       Date beginDate,
                                                       Date endDate) {

    ArrayList<Object[]> retval = new ArrayList<Object[]>();
    try {
      ProjectionList plist = Projections.projectionList();
      plist.add(Projections.property("reconciled"))
          .add(Projections.property("date"))
          .add(Projections.sum("a.amount"))
          .add(Projections.property("entity"))
          .add(Projections.property("description"))
          .add(Projections.groupProperty("id"));
      retval = (ArrayList<Object[]>)(new HibernatePortal()).detachedCriteriaQuery(
          DetachedCriteria.forClass(Transaction.class)
              .createAlias("allocations", "a")
              .createAlias("a.category", "c")
              .add(Restrictions.eq("c.account", account))
              .add(Restrictions.ge("date", beginDate))
              .add(Restrictions.le("date", endDate))
              .setProjection(plist)
              .addOrder(Order.asc("date"))
              .addOrder(Order.asc("stamp"))
          );
    } catch (EnvelopeException e) {
      logger.error(e);
    }
    return retval;
  }
}
