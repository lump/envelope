package us.lump.envelope.entity;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Type;
import us.lump.lib.Money;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;
import java.text.MessageFormat;

/**
 * A many-to-one list of Categories for a transaction.
 *
 * @author Troy Bowman
 * @version $Id: Allocation.java,v 1.1 2007/07/21 20:15:04 troy Exp $
 */
@Entity
@org.hibernate.annotations.Entity(dynamicUpdate = true)
@Table(name = "allocations")
@org.hibernate.annotations.Table(appliesTo = "allocations", fetch = org.hibernate.annotations.FetchMode.SELECT)
public class Allocation implements Identifiable {
  private Integer id;
  private Timestamp stamp;
  private Category category;
  private Transaction transaction;
  private Money amount;

  public String toString() {
    return MessageFormat.format("{0}@{1}", amount.toFormattedString(), category.toString());
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

  /**
   * Get the stamp.
   *
   * @return Timestamp
   */
  @Version
  @Column(name = "stamp", nullable = false)
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
  @ManyToOne(
      cascade = {CascadeType.PERSIST, CascadeType.MERGE},
      fetch = javax.persistence.FetchType.EAGER)
  @Fetch(value = FetchMode.SELECT)
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
  @ManyToOne(
      cascade = {CascadeType.PERSIST, CascadeType.MERGE},
      fetch = javax.persistence.FetchType.EAGER)
  @Fetch(value = FetchMode.SELECT)
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
  @Type(type = "us.lump.envelope.entity.type.MoneyType")
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

    if (amount != null ? !amount.equals(that.amount) : that.amount != null) return false;
    if (category != null ? !category.equals(that.category) : that.category != null) return false;
    if (id != null ? !id.equals(that.id) : that.id != null) return false;
    if (stamp != null ? !stamp.equals(that.stamp) : that.stamp != null) return false;
    if (transaction != null ? !transaction.equals(that.transaction) : that.transaction != null) return false;

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
