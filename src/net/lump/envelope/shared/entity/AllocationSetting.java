package us.lump.envelope.shared.entity;

import javax.persistence.*;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.MessageFormat;

/**
 * An Allocation Setting object.
 *
 * @author Troy Bowman
 * @version $Id: AllocationSetting.java,v 1.1 2009/07/13 17:21:44 troy Exp $
 */
@javax.persistence.Entity
@Table(name = "allocation_settings")
public class AllocationSetting extends Identifiable<Integer, Timestamp> implements Stampable<Timestamp> {
  public static enum AllocationSettingType {
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
  private AllocationSettingType type;
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
  public AllocationSettingType getType() {
    return type;
  }

  public void setType(AllocationSettingType type) {
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

    AllocationSetting allocationSetting = (AllocationSetting)o;

    if (budget != null
        ? !budget.equals(allocationSetting.budget)
        : allocationSetting.budget != null) return false;
    if (id != null
        ? !id.equals(allocationSetting.id)
        : allocationSetting.id != null) return false;
    if (name != null
        ? !name.equals(allocationSetting.name)
        : allocationSetting.name != null) return false;
    if (refernceDate != null
        ? !refernceDate.equals(allocationSetting.refernceDate)
        : allocationSetting.refernceDate != null) return false;
    if (stamp != null
        ? !stamp.equals(allocationSetting.stamp)
        : allocationSetting.stamp != null) return false;
    if (type.ordinal() != allocationSetting.type.ordinal()) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (id != null ? id.hashCode() : 0);
    result = 31 * result + (stamp != null ? stamp.hashCode() : 0);
    result = 31 * result + (budget != null ? budget.hashCode() : 0);
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (type != null ? type.ordinal() : 0);
    result = 31 * result + (refernceDate != null
                            ? refernceDate.toString().hashCode() : 0);
    return result;
  }
}
