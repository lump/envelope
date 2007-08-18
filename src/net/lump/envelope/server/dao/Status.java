package us.lump.envelope.server.dao;

import org.hibernate.Query;
import us.lump.envelope.entity.Account;
import us.lump.envelope.entity.Category;
import us.lump.lib.Money;

import java.util.List;

/**
 * A DAO which deals with reporting information.
 *
 * @author Troy Bowman
 * @version $Id: Status.java,v 1.1 2007/08/18 23:20:11 troy Exp $
 */
public class Status extends DAO {

  /**
   * Returns a balance for a specific category and year, depending or
   * reconciliation.
   *
   * @param category   for the query
   * @param year       for the query
   * @param reconciled whether this has been reconciled or not
   *
   * @return the balance
   */
  @SuppressWarnings({"unchecked"})
  public Money getCategoryBalance(Category category,
                                  Integer year,
                                  Boolean reconciled) {

    Query query = getCurrentSession().createQuery(
        "select sum(a.amount)" +
        "from Allocation a, Transaction t " +
        "where a.category = :id " +
        "and a.transaction = t.id " +
        "and year(t.date) = :year " +
        "and t.reconciled = :reconciled")
        .setInteger("id", category.getId())
        .setInteger("year", year)
        .setBoolean("reconciled", reconciled);

    Object o = query.iterate().next();

    return o == null ? new Money(0) : (Money)o;
  }

  /**
   * Get a list of balances for all of the categories for a specific year,
   * depending on whether they're reconciled or not.
   *
   * @param year       for the query
   * @param reconciled whether this has been reconciled or not
   *
   * @return a list containing a small array of a category and balance
   */
  @SuppressWarnings({"unchecked"})
  public List<Object> getCategoryBalances(Integer year, Boolean reconciled) {

    Query query = getCurrentSession().createQuery(
        "select c, sum(a.amount) " +
        "from Allocation a, Transaction t, Category c " +
        "where a.transaction = t.id " +
        "and year(t.date) = :year " +
        "and t.reconciled = :reconciled " +
        "and c.id = a.category " +
        "group by c.id")
        .setInteger("year", year)
        .setBoolean("reconciled", reconciled);

    return (List<Object>)query.list();
  }

  /**
   * Returns the account balance for a specific account and year, depending on
   * whether the transactions have been reconciled or not.
   *
   * @param account    for the query
   * @param year       for the query
   * @param reconciled whether this has been reconciled or not
   *
   * @return an amount of the balance
   */
  @SuppressWarnings({"unchecked"})
  public Money getAccountBalance(Account account,
                                 Integer year,
                                 Boolean reconciled) {

    Query query = getCurrentSession().createQuery(
        "select sum(al.amount) " +
        "from Allocation al, Transaction tr, Account ac, Category ca " +
        "where al.transaction = tr.id " +
        "and al.category = ca.id " +
        "and ca.account = ac.id " +
        "and year(tr.date) = :year " +
        "and tr.reconciled = :reconciled " +
        "and ac.id = :account")
        .setInteger("year", year)
        .setBoolean("reconciled", reconciled)
        .setInteger("account", account.getId());

    Object o = query.iterate().next();
    return o == null ? new Money(0) : (Money)o;
  }

  /**
   * Retrieve a list of balances for all accounts for a specific year, and
   * depending on whether the transactions have been reconciled.
   *
   * @param year       for the query
   * @param reconciled whether this has been reconciled or not *
   *
   * @return an array
   */
  @SuppressWarnings({"unchecked"})
  public List<Object[]> getAccountBalances(Integer year, Boolean reconciled) {

    Query query = getCurrentSession().createQuery(
        "select ac, sum(al.amount) " +
        "from Allocation al, Transaction tr, Account ac, Category ca " +
        "where al.transaction = tr.id " +
        "and al.category = ca.id " +
        "and ca.account = ac.id " +
        "and year(tr.date) = :year " +
        "and tr.reconciled = :reconciled " +
        "group by ac.id")
        .setInteger("year", year)
        .setBoolean("reconciled", reconciled);

    return (List<Object[]>)query.list();
  }

}
