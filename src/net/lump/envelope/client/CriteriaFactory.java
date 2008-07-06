package us.lump.envelope.client;

import org.hibernate.criterion.*;
import org.apache.log4j.Logger;
import us.lump.envelope.entity.User;
import us.lump.envelope.entity.Budget;
import us.lump.envelope.entity.Account;
import us.lump.envelope.entity.Category;
import us.lump.envelope.client.portal.HibernatePortal;
import us.lump.envelope.server.exception.EnvelopeException;

import java.util.List;
import java.util.ArrayList;

/**
 * Creates detached criteria queries.
 *
 * @author Troy Bowman
 * @version $Id: CriteriaFactory.java,v 1.1 2008/07/06 04:14:24 troy Exp $
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

  public List<Category> getCategoriesforBudget(Budget budget) {
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
}
