package us.lump.envelope.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * A Category.
 * <p/>
 * A Budget Category contains allocations for expenses and income, and the sum
 * of all reconciled balances for allocations in all categories for an account
 * should match the account balance.
 *
 * @author Troy Bowman
 * @version $Id: Category.java,v 1.6 2008/02/29 04:18:23 troy Exp $
 */
@javax.persistence.Entity
@Table(name = "categories")
public class Category implements Identifiable {

  private Integer id;
  private Timestamp stamp;
  private Account account;
  private String name;

  public String toString() {
    return name;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  public Integer getId() {
    return id;
  }

  public void setId(Serializable id) {
    this.id = (Integer)id;
  }

  @Version
  @Column(name = "stamp", nullable = false)
  public Timestamp getStamp() {
    return stamp;
  }

  public void setStamp(Timestamp stamp) {
    this.stamp = stamp;
  }

  /**
   * Gets the Account associated with this Category.
   *
   * @return Account.
   */
  @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  @JoinColumn(name = "account")
  public Account getAccount() {
    return account;
  }

  /**
   * Sets the Account associated with this Category.
   *
   * @param account Account
   *
   * @see Account
   */
  public void setAccount(Account account) {
    this.account = account;
  }

  /**
   * Gets the Category name.
   *
   * @return String
   *
   * @see Category
   */
  @Column(name = "name", nullable = false, length = 64)
  public String getName() {
    return name;
  }

  /**
   * Sets the Category name.
   *
   * @param name String
   *
   * @see Category
   */
  public void setName(String name) {
    this.name = name;
  }

  /* this is inefficient and we can't provide arguments

  @Formula(value = "(select sum(a.amount) " +
      "from allocations a, transactions t " +
      "where a.category = id " +
      "and a.transaction = t.id and year(t.date) = year(now()))")
  @Type(type = "us.lump.envelope.entity.type.MoneyType")
  public Money getBalance() {
    return balance;
  }

  public void setBalance(Money balance) {
    this.balance = balance == null ? new Money("0.0") : balance;
  }

  @Formula(value = "(select sum(a.amount) " +
      "from allocations a, transactions t " +
      "where a.category = id " +
      "and a.transaction = t.id and year(t.date) = year(now()) and t.reconciled != 0)")
  @Type(type = "us.lump.envelope.entity.type.MoneyType")
  public Money getReconciledBalance() {
    return reconciledBalance;
  }

  public void setReconciledBalance(Money balance) {
    this.reconciledBalance = balance == null ? new Money("0.0") : balance;
  }
  */

  @SuppressWarnings({"RedundantIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Category category = (Category)o;

    if (account != null
        ? !account.equals(category.account)
        : category.account != null) return false;
    if (id != null
        ? !id.equals(category.id)
        : category.id != null) return false;
    if (name != null
        ? !name.equals(category.name)
        : category.name != null) return false;
    if (stamp != null
        ? !stamp.equals(category.stamp)
        : category.stamp != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (id != null ? id.hashCode() : 0);
    result = 31 * result + (stamp != null ? stamp.hashCode() : 0);
    result = 31 * result + (account != null ? account.hashCode() : 0);
    result = 31 * result + (name != null ? name.hashCode() : 0);
    return result;
  }
}
