package net.lump.envelope.shared.entity;

import net.lump.lib.Money;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import jakarta.persistence.*;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.util.*;

/**
 * A transaction.
 *
 * @author Troy Bowman
 * @version $Id: Transaction.java,v 1.3 2010/01/04 06:07:24 troy Exp $
 */
@jakarta.persistence.Entity
@org.hibernate.annotations.Entity(dynamicUpdate = true)
@Table(name = "transactions")
@org.hibernate.annotations.Table(
    appliesTo = "transactions",
    fetch = org.hibernate.annotations.FetchMode.SELECT)
public class Transaction extends Identifiable<Integer, Timestamp> {

  private Integer id;
  private Timestamp stamp;
  private Date date;
  private List<Allocation> allocations = new ArrayList<Allocation>();
  private String entity;
  private String description;
  private Boolean reconciled;
  private Boolean transfer;

  public String toString() {
    String out = MessageFormat.format("{0,date,short} {1} {2}/{3}",
        date,
        getNetAmount().toString(),
        entity,
        description);
    for (Allocation a : allocations)
      out += System.getProperty("line.separator") + a.toString();
    return out;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
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

  @Column(name = "date", nullable = false)
  public Date getDate() {
    return date;
  }

  public void setDate(Date date) {
    this.date = date;
  }

  @OneToMany(mappedBy = "transaction", fetch = FetchType.EAGER)
  @Fetch(value = FetchMode.SELECT )
  public List<Allocation> getAllocations() {
    return allocations;
  }

  public void setAllocations(List<Allocation> allocations) {
    this.allocations = allocations;
    /*
    this.allocations.clear();
    if (allocations != null)
      this.allocations.addAll(allocations);
      */
  }

  @Column(name = "entity", nullable = false, length = 128)
  public String getEntity() {
    return entity;
  }

  public void setEntity(String entity) {
    this.entity = entity;
  }

  @Column(name = "description", nullable = false, length = 1024)
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


  @Column(name = "transfer", nullable = false)
  public Boolean getTransfer() {
    return transfer;
  }

  public void setTransfer(Boolean transfer) {
    this.transfer = transfer;
  }

  @Transient
  public Money getNetAmount() {
    Money total = new Money(0);

    for (Allocation a : this.getAllocations())
      if (a.getAmount() != null)
        total = total.add(a.getAmount());

    return total;
  }

  @Transient
  public Money getIncomeAmount() {
    Money total = new Money(0);

    for (Allocation a : this.getAllocations())
      if (a.getAmount() != null && a.getAmount().compareTo(Money.ZERO) > 0)
        total = total.add(a.getAmount());

    return total;
  }

  @Transient
  public Money getDebitAmount() {
    Money total = new Money(0);

    for (Allocation a : this.getAllocations())
      if (a.getAmount() != null && a.getAmount().compareTo(Money.ZERO) < 0)
        total = total.add(a.getAmount());

    return total;
  }

  @SuppressWarnings({"RedundantIfStatement", "SimplifiableIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Transaction that = (Transaction)o;

    if (id != null
        ? !id.equals(that.id)
        : that.id != null) return false;
    if (date != null
        ? !date.equals(that.date)
        : that.date != null) return false;
    if (description != null
        ? !description.equals(that.description)
        : that.description != null) return false;
    if (reconciled != null
        ? !reconciled.equals(that.reconciled)
        : that.reconciled != null) return false;
    if (transfer != null
        ? !transfer.equals(that.transfer)
        : that.transfer != null) return false;
    if (stamp != null
        ? !stamp.equals(that.stamp)
        : that.stamp != null) return false;
    if (entity != null
        ? !entity.equals(that.entity)
        : that.entity != null) return false;

    // Allocations are deliberately NOT compared here.
    //
    // What used to stand here built both sides of the comparison from
    // this.allocations -- the second copy was unqualified -- so it compared the
    // list to itself and always passed.  It was not merely useless: its sort
    // comparator dereferenced one.getId(), and the moment the form's graph held
    // two allocations with a null id among them (the empty row TransactionForm
    // appends on Tab or Down, which sendAllocationChange then refuses to save, so
    // it persists for the life of the form) Collections.sort threw a
    // NullPointerException out of equals.  The only caller is
    // TransactionChangeHandler.sendChanges, on the EDT and outside any try, so
    // every description, entity and date edit was silently dropped from then on.
    //
    // Repointing the second copy at that.allocations is not the fix: Allocation
    // .equals compares its transaction, so the two would recurse into each other
    // and overflow the stack for any two transactions whose scalar fields match.
    // The self-comparison was the only thing holding that off.  Allocation edits
    // do not travel through here at all -- they are saved on their own by
    // TransactionChangeHandler.sendAllocationChange -- so this compares the
    // transaction's own columns, which is all its one caller needs.

    return true;

  }

  public int hashCode() {
    int result;
    result = (id != null ? id.hashCode() : 0);
    result = 31 * result + (stamp != null ? stamp.hashCode() : 0);
    result = 31 * result + (date != null ? date.hashCode() : 0);

//    if (this.getAllocations() != null)
//      for (Allocation a : this.getAllocations())
//        result = 31 * result + (a != null ? a.hashCode() : 0);

    result = 31 * result + (entity != null ? entity.hashCode() : 0);
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + (reconciled != null ? reconciled.hashCode() : 0);
    result = 31 * result + (transfer != null ? transfer.hashCode() : 0);
    return result;
  }
}
