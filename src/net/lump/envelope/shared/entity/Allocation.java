package net.lump.envelope.shared.entity;

import net.lump.lib.Money;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.sql.Timestamp;
import java.text.MessageFormat;

/**
 * A many-to-one list of Allocations for a Transaction.  Allocations are tied to Categories, which are tied to Accounts.
 *
 * @author Troy Bowman
 * @version $Id: Allocation.java,v 1.5 2010/01/06 06:58:01 troy Exp $
 */
@javax.persistence.Entity
@org.hibernate.annotations.Entity(dynamicUpdate = true)
@Table(name = "allocations")
@org.hibernate.annotations.Table(appliesTo = "allocations")
public class Allocation extends Identifiable<Integer, Timestamp> {
  private Integer id;
  private Timestamp stamp;
  private Category category;
  private Transaction transaction;
  private Money amount;

  public String toString() {
    String out = MessageFormat.format("{0}@{1}", amount.toString(), category.toString());
    return out;
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

  /**
   * Get the stamp.
   *
   * @return Timestamp
   */
  @Version
  @Column(name = "stamp", nullable = false)
  @Override
  public Timestamp getStamp() {
    return stamp;
  }

  /**
   * Set the stamp.
   *
   * @param stamp Timestamp
   */
  public void setStamp(Timestamp stamp) {
    this.stamp = stamp;
  }

  /**
   * Get the Category of this Allocation.
   *
   * @return Category
   */
  @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  @JoinColumn(name = "category")
  public Category getCategory() {
    return category;
  }

  /**
   * Set the Category of this Allocation.
   *
   * @param category Category
   */
  public void setCategory(Category category) {
    this.category = category;
  }


  /**
   * Get the Transaction associated with this Allocation.
   *
   * @return Transaction
   */
  @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  @JoinColumn(name = "transaction")
  public Transaction getTransaction() {
    return transaction;
  }

  /**
   * Set the Transaction associated with this Allocation.
   *
   * @param transaction Transaction
   */
  public void setTransaction(Transaction transaction) {
    this.transaction = transaction;
  }

  /**
   * Get the monetary amount of this Allocation.
   *
   * @return Money
   */
  @Column(name = "amount", nullable = false, precision = 2)
  @Type(type = "net.lump.envelope.shared.entity.type.MoneyType")
  public Money getAmount() {
    return amount;
  }

  /**
   * Set the monetary amount of this Allocation.
   *
   * @param amount Money
   */
  public void setAmount(Money amount) {
    this.amount = amount;
  }

  @SuppressWarnings({"RedundantIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Allocation that = (Allocation)o;

    if (amount != null
        ? !amount.equals(that.amount)
        : that.amount != null) return false;
    if (category != null
        ? !category.equals(that.category)
        : that.category != null) return false;
    if (id != null
        ? !id.equals(that.id)
        : that.id != null) return false;
    if (stamp != null
        ? !stamp.equals(that.stamp)
        : that.stamp != null) return false;
    if (transaction != null
        ? !transaction.equals(that.transaction)
        : that.transaction != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (id != null ? id.hashCode() : 0);
    result = 31 * result + (stamp != null ? stamp.hashCode() : 0);
    result = 31 * result + (category != null ? category.hashCode() : 0);
    result = 31 * result + (transaction != null ? transaction.hashCode() : 0);
    result = 31 * result + (amount != null ? amount.hashCode() : 0);

    return result;
  }
}
