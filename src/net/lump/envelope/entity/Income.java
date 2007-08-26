package us.lump.envelope.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.MessageFormat;

/**
 * An income object.
 *
 * @author Troy Bowman
 * @version $Id: Income.java,v 1.3 2007/08/26 06:28:57 troy Exp $
 */
@javax.persistence.Entity
@Table(name = "incomes")
public class Income implements Identifiable {
  public static enum IncomeType {
    Reimbursement,
    Weekly_Payday,
    Biweekly_Payday,
    Semimonthly_Payday,
    Monthly_Payday
  }

  private Integer id;
  private Timestamp stamp;
  private Budget budget;
  private String name;
  private IncomeType type;
  private Date refernceDate;

  public String toString() {
    return MessageFormat.format("{0}:{2}@{3}",
                                name,
                                type.toString(),
                                budget.toString());
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

  @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  @JoinColumn(name = "budget")
  public Budget getBudget(Budget budget) {
    return this.budget;
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
  public IncomeType getType() {
    return type;
  }

  public void setType(IncomeType type) {
    this.type = type;
  }

  @Column(name = "reference_date")
  public Date getRefernceDate() {
    return refernceDate;
  }

  public void setRefernceDate(Date refernceDate) {
    this.refernceDate = refernceDate;
  }


  @SuppressWarnings({"RedundantIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Income income = (Income)o;

    if (budget != null
        ? !budget.equals(income.budget)
        : income.budget != null) return false;
    if (id != null
        ? !id.equals(income.id)
        : income.id != null) return false;
    if (name != null
        ? !name.equals(income.name)
        : income.name != null) return false;
    if (refernceDate != null
        ? !refernceDate.equals(income.refernceDate)
        : income.refernceDate != null) return false;
    if (stamp != null
        ? !stamp.equals(income.stamp)
        : income.stamp != null) return false;
    if (type != income.type) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (id != null ? id.hashCode() : 0);
    result = 31 * result + (stamp != null ? stamp.hashCode() : 0);
    result = 31 * result + (budget != null ? budget.hashCode() : 0);
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (type != null ? type.hashCode() : 0);
    result = 31 * result + (refernceDate != null ? refernceDate.hashCode() : 0);
    return result;
  }
}
