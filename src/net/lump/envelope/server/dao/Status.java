package us.lump.envelope.server.dao;

import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.criterion.Restrictions;
import us.lump.envelope.entity.Account;
import us.lump.envelope.entity.Category;
import us.lump.lib.Money;

import java.util.List;

/**
 * A DAO which deals with reporting information.
 *
 * @author Troy Bowman
 * @version $Id: Status.java,v 1.3 2008/02/29 04:18:23 troy Exp $
 */
public class Status extends DAO {

  /**
   * Returns a balance for a specific category and year, depending or
   * reconciliation.
   *
   * @param category   for the query
   * @param reconciled whether this has been reconciled or not.  a null Boolean
   *                   will query both reconiled and not reconciled.
   *
   * @return the balance
   */
  @SuppressWarnings({"unchecked"})
  public Money getCategoryBalance(Category category,
                                  Boolean reconciled) {
    String q =
        "select sum(a.amount) " +
        "from Allocation a, Transaction t, Category c, Account n " +
        "where a.category = :id " +
        "and a.transaction = t.id " +
        "and c.account = n.id " +
        "and n.budget = :budget ";
    if (reconciled != null) q += "and t.reconciled = :reconciled";

    Query query = getCurrentSession().createQuery(q)
        .setEntity("id", category)
        .setEntity("budget", getUser().getBudget());
    if (reconciled != null) query.setBoolean("reconciled", reconciled);


    return (Money)query.uniqueResult();
  }

  /**
   * Get a list of balances for all of the categories for a specific year,
   * depending on whether they're reconciled or not.
   *
   * @param reconciled whether this has been reconciled or not. a null Boolean
   *                   will query regardless of reconciled.
   *
   * @return a list containing a small array of a category and balance
   */
  @SuppressWarnings({"unchecked"})
  public List<Object> getCategoryBalances(Boolean reconciled) {
    String q =
        "select c.name, sum(a.amount) "
        + "from Allocation a, Transaction t, Category c, Account n "
        + "where a.transaction = t.id "
        + "and a.category = c.id "
        + "and c.account = n.id "
        + "and n.budget = :budget ";
    if (reconciled != null) q += "and t.reconciled = :reconciled ";
    q += "group by c.id";

    Query query = getCurrentSession().createQuery(q)
        .setInteger("budget", getUser().getBudget().getId().intValue());
    if (reconciled != null) query.setBoolean("reconciled", reconciled);
    return (List<Object>)query.list();
  }

  /**
   * Returns the account balance for a specific account and year, depending on
   * whether the transactions have been reconciled or not.
   *
   * @param account    for the query
   * @param reconciled whether this has been reconciled or not
   *
   * @return an amount of the balance
   */
  @SuppressWarnings({"unchecked"})
  public Money getAccountBalance(Account account,
                                 Boolean reconciled) {

    String q =
        "select sum(a.amount) "
        + "from Allocation a, Transaction t, Category c, Account n "
        + "where a.transaction = t.id "
        + "and a.category = c.id "
        + "and c.account = n.id "
        + "and n.id = :account "
        + "and n.budget = :budget ";
    if (reconciled != null) q += "and t.reconciled = :reconciled ";

    Query query = getCurrentSession().createQuery(q)
        .setInteger("account", account.getId())
        .setInteger("budget", getUser().getBudget().getId().intValue());
    if (null != reconciled) query.setBoolean("reconciled", reconciled);

    return (Money)query.uniqueResult();
  }

  /**
   * Retrieve a list of balances for all accounts for a specific year, and
   * depending on whether the transactions have been reconciled.
   *
   * @param reconciled whether this has been reconciled or not *
   *
   * @return an array
   */
  @SuppressWarnings({"unchecked"})
  public List<Object[]> getAccountBalances(Boolean reconciled) {
    String q =
        "select n.id, n.name, sum(a.amount) "
        + "from Allocation a, Transaction t, Category c, Account n "
        + "where a.transaction = t.id and "
        + "a.category = c.id "
        + "and c.account = n.id "
        + "and n.budget = :budget ";
    if (reconciled != null) q += "and t.reconciled = :reconciled ";
    q += "group by n.id";

    Query query = getCurrentSession().createQuery(q)
        .setInteger("budget", getUser().getBudget().getId().intValue());
    if (reconciled != null) query.setBoolean("reconciled", reconciled);

    return (List<Object[]>)query.list();
  }

  @SuppressWarnings({"unchecked"})
  public Category getCategory(String categoryName) {

    Query query = getCurrentSession().createQuery(
        "select c "
        + "from Category c, Account a, Budget b "
        + "where c.account = a.id "
        + "and a.budget = :budget "
        + "and c.name = :name")
        .setInteger("budget", getUser().getBudget().getId().intValue())
        .setString("name", categoryName);

    return (Category)query.uniqueResult();
  }

  @SuppressWarnings({"unchecked"})
  public Account getAccount(String accountName) {

    Criteria criteria = getCurrentSession().createCriteria(Account.class);
    criteria.add(Restrictions.eq("budget", getUser().getBudget()));
    criteria.add(Restrictions.eq("name", accountName));
    return (Account)criteria.uniqueResult();
  }

}
