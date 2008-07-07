package us.lump.envelope.client;

import org.hibernate.criterion.*;
import org.hibernate.FetchMode;
import org.apache.log4j.Logger;
import us.lump.envelope.entity.*;
import us.lump.envelope.client.portal.HibernatePortal;
import us.lump.envelope.server.exception.EnvelopeException;

import java.util.List;
import java.util.ArrayList;
import java.util.Date;

/**
 * Creates detached criteria queries.
 *
 * @author Troy Bowman
 * @version $Id: CriteriaFactory.java,v 1.3 2008/07/07 06:04:34 troy Exp $
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

  public List<Allocation> getAllocationsForCategory(Category category, Date date) {
    List<Allocation> retval = new ArrayList<Allocation>();
    try {
      retval = (List<Allocation>)(new HibernatePortal()).detachedCriteriaQuery(
          DetachedCriteria.forClass(Allocation.class)
              .createAlias("transaction", "t")
              .add(Restrictions.eq("category", category))
              .add(Restrictions.gt("t.date", date))
              .addOrder(Order.asc("t.date"))
      );
    } catch (EnvelopeException e) {
      logger.error(e);
    }
    return retval;
  }

  public ArrayList<Object[]> getTransactionsForAccount(Account account) {
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
              .setProjection(plist)
              .addOrder(Order.asc("date"))
          );
    } catch (EnvelopeException e) {
      logger.error(e);
    }
    return retval;
  }
}
