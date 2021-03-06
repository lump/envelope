package net.lump.envelope.shared.entity;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import javax.persistence.*;
import java.sql.Timestamp;

/**
 * A Category.
 * <p/>
 * A Budget Category contains allocations for expenses and income, and the sum
 * of all reconciled balances for allocations in all categories for an account
 * should match the account balance.
 *
 * @author Troy Bowman
 * @version $Id: Category.java,v 1.3 2009/10/02 22:06:23 troy Exp $
 */
@javax.persistence.Entity
@Table(name = "categories")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Category extends Identifiable<Integer, Timestamp> implements Comparable<Category> {

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
  @Override
  public Integer getId() {
    return id;
  }

  @Override
  public void setId(Integer id) {
    this.id = id;
  }

  @Version
  @Column(name = "stamp", nullable = false)
  @Override
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
  @Fetch(value = FetchMode.JOIN)
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
  @Type(type = "net.lump.envelope.shared.entity.type.MoneyType")
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
  @Type(type = "net.lump.envelope.shared.entity.type.MoneyType")
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

    if (id != null
        ? !id.equals(category.id)
        : category.id != null) return false;
    if (account != null) {
      if (category.account == null)
        return false;
      else if (!account.getId().equals(category.account.getId()))
        return false;
    }
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

  public int compareTo(Category that) {
    return this.name.compareTo(that.name);
  }
}
