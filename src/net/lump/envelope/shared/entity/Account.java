package net.lump.envelope.shared.entity;

import org.hibernate.annotations.Type;
import net.lump.lib.Money;

import javax.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

/**
 * An account object.
 *
 * @author Troy Bowman
 * @version $Id: Account.java,v 1.4 2010/07/28 04:25:04 troy Exp $
 */
@javax.persistence.Entity
@Table(name = "accounts")
public class Account extends Identifiable<Integer, Timestamp>
    implements Comparable<Account> {
//  public static final long serialVersionUID = Long.parseLong("$Revision: 1.4 $".replaceAll("\\D", ""));

  /** The type of an Account. */
  public static enum AccountType {
    /** A debit account. This would most likely be your checking account. */
    Debit,
    /**
     * A credit account. This could be a credit card or a home equity line of
     * credit.
     */
    Credit,
    Loan
  }

  private Integer id;
  private Timestamp stamp;
  private Budget budget;
  private String name;
  private AccountType type;
  private List<Category> categories;
  private BigDecimal rate;
  private Money celing;

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

  @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  @JoinColumn(name = "budget")
  public Budget getBudget() {
    return budget;
  }

  public void setBudget(Budget budget) {
    this.budget = budget;
  }

  @Column(name = "name", nullable = false, length = 64)
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Column(name = "type", nullable = false)
  @Enumerated(value = javax.persistence.EnumType.STRING)
  public AccountType getType() {
    return type;
  }

  public void setType(AccountType type) {
    this.type = type;
  }

  @OneToMany(mappedBy = "account")
  public List<Category> getCategories() {
    return this.categories;
  }

  public void setCategories(List<Category> list) {
    categories = list;
  }

  @Column(name = "rate", nullable = false)
  public BigDecimal getRate() {
    return rate;
  }

  public void setRate(BigDecimal rate) {
    this.rate = rate;
  }

  @Column(name = "ceiling", nullable = false)
  @Type(type = "net.lump.envelope.shared.entity.type.MoneyType")
  public Money getCeling() {
    return celing;
  }

  public void setCeling(Money celing) {
    this.celing = celing;
  }

  /* this relies on an inefficent and inflexible paradigm.  buzzword. bzzzt.
  @Transient
  public Money getBalance() {
    BigDecimal balance = new Money("0.0");
    for (Category c : getCategories()) {
      if (c.getBalance() != null)
        balance = balance.add(c.getBalance());
    }
    return new Money(balance);
  }

  @Transient
  public Money getReconciledBalance() {
    BigDecimal balance = new Money("0.0");
    for (Category c : getCategories()) {
      if (c.getReconciledBalance() != null)
        balance = balance.add(c.getReconciledBalance());
    }
    return new Money(balance);
  }
  */

  @SuppressWarnings({"RedundantIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Account account = (Account)o;

    if (budget != null
        ? !budget.equals(account.budget)
        : account.budget != null) return false;
    if (categories != null
        ? !categories.equals(account.categories)
        : account.categories != null) return false;
    if (id != null
        ? !id.equals(account.id)
        : account.id != null) return false;
    if (celing != null
        ? !celing.equals(account.celing)
        : account.celing != null) return false;
    if (name != null
        ? !name.equals(account.name)
        : account.name != null) return false;
    if (rate != null
        ? !rate.equals(account.rate)
        : account.rate != null) return false;
    if (stamp != null
        ? !stamp.equals(account.stamp)
        : account.stamp != null) return false;
    if (type.ordinal() != account.type.ordinal()) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (id != null ? id.hashCode() : 0);
    result = 31 * result + (stamp != null ? stamp.hashCode() : 0);
    result = 31 * result + (budget != null ? budget.hashCode() : 0);
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (type != null ? type.ordinal() : 0);
    result = 31 * result + (rate != null ? rate.hashCode() : 0);
    result = 31 * result + (celing != null ? celing.hashCode() : 0);
    return result;
  }

  public int compareTo(Account that) {
    // If the names are different, we can sort by that, but if they're the same,
    // don't return 0 unless the Ids are actually the same.
    int name = this.getName().compareTo(that.getName());
    return name == 0 ? this.getId().compareTo(that.getId()) : name;

  }
}
