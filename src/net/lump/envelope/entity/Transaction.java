package us.lump.envelope.entity;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import us.lump.lib.Money;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.util.List;

/**
 * A transaction.
 *
 * @author Troy Bowman
 * @version $Id: Transaction.java,v 1.2 2007/08/07 01:08:03 troy Exp $
 */
@Entity
@org.hibernate.annotations.Entity(dynamicUpdate = true)
@Table(name = "transactions")
@org.hibernate.annotations.Table(appliesTo = "transactions", fetch = org.hibernate.annotations.FetchMode.SELECT)
public class Transaction implements Identifiable {
  private Integer id;
  private Timestamp stamp;
  private Date date;
  private List<Allocation> allocations;
  private String subcategory;
  private String who;
  private String description;
  private Boolean reconciled;

  public String toString() {
    String out = MessageFormat.format("{0,date,short} {1} {2}/{3}/{4}" + System.getProperty("line.separator"),
        date, getAmount().toFormattedString(), subcategory, who, description);
    for (Allocation a : allocations) out += a.toString() + System.getProperty("line.separator");
    return out;
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

  @Column(name = "date", nullable = false)
  public Date getDate() {
    return date;
  }

  public void setDate(Date date) {
    this.date = date;
  }

  @OneToMany(mappedBy = "transaction", fetch = javax.persistence.FetchType.EAGER)
  @Fetch(value = FetchMode.SUBSELECT)
  public List<Allocation> getAllocations() {
    return allocations;
  }

  public void setAllocations(List<Allocation> allocations) {
    this.allocations = allocations;
  }

  @Column(name = "subcategory", nullable = false, length = 64)
  public String getSubcategory() {
    return subcategory;
  }

  public void setSubcategory(String subcategory) {
    this.subcategory = subcategory;
  }

  @Column(name = "who", nullable = false, length = 128)
  public String getWho() {
    return who;
  }

  public void setWho(String who) {
    this.who = who;
  }

  @Column(name = "description", nullable = false, length = 255)
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @Column(name = "reconciled", nullable = false)
  public Boolean getReconciled() {
    return reconciled;
  }

  public void setReconciled(Boolean reconciled) {
    this.reconciled = reconciled;
  }

  @Transient
  public Money getAmount() {
    Money total = new Money(0);

    for (Allocation a : this.getAllocations())
      total = new Money(total.add(a.getAmount()).toString());

    return total;
  }

  @SuppressWarnings({"RedundantIfStatement", "SimplifiableIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Transaction that = (Transaction)o;

    if (this.getAllocations() != null && that.getAllocations() != null) {
      if (this.getAllocations() != that.getAllocations()) return false;
    }
    if (allocations != null ? !allocations.equals(that.allocations) : that.allocations != null) return false;
    if (date != null ? !date.equals(that.date) : that.date != null) return false;
    if (description != null ? !description.equals(that.description) : that.description != null) return false;
    if (id != null ? !id.equals(that.id) : that.id != null) return false;
    if (reconciled != null ? !reconciled.equals(that.reconciled) : that.reconciled != null) return false;
    if (stamp != null ? !stamp.equals(that.stamp) : that.stamp != null) return false;
    if (subcategory != null ? !subcategory.equals(that.subcategory) : that.subcategory != null) return false;
    return !(who != null ? !who.equals(that.who) : that.who != null);

  }

  public int hashCode() {
    int result;
    result = (id != null ? id.hashCode() : 0);
    result = 31 * result + (stamp != null ? stamp.hashCode() : 0);
    result = 31 * result + (date != null ? date.hashCode() : 0);

    if (this.getAllocations() != null)
      for (Allocation a : this.getAllocations())
        result = 31 * result + (a != null ? a.hashCode() : 0);

    result = 31 * result + (subcategory != null ? subcategory.hashCode() : 0);
    result = 31 * result + (who != null ? who.hashCode() : 0);
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + (reconciled != null ? reconciled.hashCode() : 0);
    return result;
  }
}
