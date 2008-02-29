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
 * @version $Id: Status.java,v 1.4 2008/02/29 05:32:48 troy Exp $
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
    String select =
        "select sum(a.amount) from Allocation a, Category c, Account n";
    String where =
        " where a.category = c and c.account = n " +
        "and n.budget = :budget and c = :category ";
    if (reconciled != null) {
      select += ", Transaction t";
      where += "and a.transaction = t and t.reconciled = :reconciled";
    }

    Query query = getCurrentSession().createQuery(select + where)
        .setEntity("category", category)
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
    String select =
        "select c.name, sum(a.amount) from Allocation a, Category c, Account n";
    String where =
        " where a.category = c and c.account = n and n.budget = :budget ";
    if (reconciled != null) {
      select += ", Transaction t";
      where += "and a.transaction = t and t.reconciled = :reconciled ";
    }

    Query query =
        getCurrentSession().createQuery(select + where + " group by c")
            .setEntity("budget", getUser().getBudget());
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

    String select =
        "select sum(a.amount) from Allocation a, Category c, Account n";
    String where =
        " where a.category = c "
        + "and c.account = n "
        + "and n = :account "
        + "and n.budget = :budget ";
    if (reconciled != null) {
      select += ", Transaction t";
      where += "and a.transaction = t.id and t.reconciled = :reconciled";
    }

    Query query = getCurrentSession().createQuery(select + where)
        .setEntity("account", account)
        .setEntity("budget", getUser().getBudget());
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
    String select =
        "select n.id, n.name, sum(a.amount) "
        + "from Allocation a, Category c, Account n";

    String where =
        " where a.category = c "
        + "and c.account = n "
        + "and n.budget = :budget ";
    if (reconciled != null) {
      select += ", Transaction t";
      where += "and a.transaction = t and t.reconciled = :reconciled ";
    }

    Query query =
        getCurrentSession().createQuery(select + where + " group by n")
            .setEntity("budget", getUser().getBudget());
    if (reconciled != null) query.setBoolean("reconciled", reconciled);

    return (List<Object[]>)query.list();
  }

  @SuppressWarnings({"unchecked"})
  public Category getCategory(String categoryName) {

    Query query = getCurrentSession().createQuery(
        "select c "
        + "from Category c, Account a, Budget b "
        + "where c.account = a "
        + "and a.budget = :budget "
        + "and c.name = :name")
        .setEntity("budget", getUser().getBudget())
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
