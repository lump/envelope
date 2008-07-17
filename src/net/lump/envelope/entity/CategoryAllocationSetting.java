package us.lump.envelope.entity;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Allocation Setting for each Category.
 *
 * @author troy
 * @version $Id: CategoryAllocationSetting.java,v 1.1 2008/02/29 04:18:23 troy
 *          Exp $
 */
@javax.persistence.Entity
@Table(name = "categories")
public class CategoryAllocationSetting extends Identifiable<Integer, Timestamp> {
  public static enum AllocationType {
    /** Percent per paycheck */
    ppp,
    /** Fixed per paycheck */
    fpp,
    /** Fixed per month */
    fpm
  }

  private Integer id;
  private Timestamp stamp;
  private AllocationSetting allocationSetting;
  private Category category;
  private BigDecimal allocation = new BigDecimal(0.0);
  private AllocationType allocationType = AllocationType.ppp;
  private Boolean autoDeduct = false;


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
   * This refers to the allocation setting, which contains general settings for
   * these categorized settings.
   *
   * @return AllocationSetting
   */
  @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Fetch(value = FetchMode.SELECT)
  @JoinColumn(name = "allocationCategory")
  public AllocationSetting getAllocationSetting() {
    return allocationSetting;
  }

  public void setAllocationSetting(AllocationSetting allocationSetting) {
    this.allocationSetting = allocationSetting;
  }

  /**
   * Get the Category of this Allocation Setting
   *
   * @return Category
   */
  @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
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
   * Gets the Allocation ammount.  This could be a monetary amount, or it could
   * be a percentage, depending on the allocation type.
   *
   * @return BigDecimal
   */
  @Column(name = "allocation", nullable = false)
  public BigDecimal getAllocation() {
    return allocation;
  }

  /**
   * Sets the Allocation ammount.  This could be a monetary amount, or it could
   * be a percentage, depending on the allocation type.
   *
   * @param allocation BigDecimal
   */
  public void setAllocation(BigDecimal allocation) {
    this.allocation = allocation;
  }

  /**
   * Gets the AllocationType.
   *
   * @return AllocationType.
   *
   * @see AllocationType
   */
  @Column(name = "allocation_type", nullable = false)
  @Enumerated(value = javax.persistence.EnumType.STRING)
  public AllocationType getAllocationType() {
    return allocationType;
  }

  /**
   * Sets the AllocationType.
   *
   * @param allocationType AllocationType
   *
   * @see AllocationType
   */
  public void setAllocationType(AllocationType allocationType) {
    this.allocationType = allocationType;
  }

  /**
   * Whether this category, when income is added, should create an opposing
   * auto-deducted transaction. This allows you to keep a record of transactions
   * of all of the automatically deducted taxes, 401k payments, insurance
   * premiums, and so on from your gross.
   *
   * @return Boolean
   */
  @Column(name = "auto_deduct", nullable = false)
  public Boolean getAutoDeduct() {
    return autoDeduct;
  }

  /**
   * Set the autoDeduct flag.
   *
   * @param autoDeduct Boolean
   */
  public void setAutoDeduct(Boolean autoDeduct) {
    this.autoDeduct = autoDeduct;
  }

  @SuppressWarnings({"RedundantIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CategoryAllocationSetting)) return false;

    CategoryAllocationSetting that = (CategoryAllocationSetting)o;

    if (allocation != null
        ? !allocation.equals(that.allocation)
        : that.allocation != null) return false;
    if (allocationSetting != null
        ? !allocationSetting.equals(that.allocationSetting)
        : that.allocationSetting != null) return false;
    if (allocationType != that.allocationType) return false;
    if (autoDeduct != null
        ? !autoDeduct.equals(that.autoDeduct)
        : that.autoDeduct != null) return false;
    if (category != null
        ? !category.equals(that.category)
        : that.category != null) return false;
    if (id != null ? !id.equals(that.id) : that.id != null) return false;
    if (stamp != null ? !stamp.equals(that.stamp) : that.stamp != null)
      return false;

    return true;
  }


  public int hashCode() {
    int result;
    result = (id != null ? id.hashCode() : 0);
    result = 31 * result + (stamp != null ? stamp.hashCode() : 0);
    result = 31 * result + (allocationSetting != null
                            ? allocationSetting.hashCode()
                            : 0);
    result = 31 * result + (category != null ? category.hashCode() : 0);
    result = 31 * result + (allocation != null ? allocation.hashCode() : 0);
    result =
        31 * result + (allocationType != null ? allocationType.ordinal() : 0);
    result = 31 * result + (autoDeduct != null ? autoDeduct.hashCode() : 0);
    return result;
  }

}
