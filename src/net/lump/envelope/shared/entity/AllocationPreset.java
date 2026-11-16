package net.lump.envelope.shared.entity;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import jakarta.persistence.*;
import net.lump.lib.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;

/**
 * A record which maintains an allocation preset.
 *
 * @author troy
 */
@jakarta.persistence.Entity
@Table(name = "allocation_presets")
public class AllocationPreset extends Identifiable<Integer, Timestamp> implements Stampable<Timestamp> {
  public static enum AllocationType {
    percent,
    fixed,
  }

  private static final BigDecimal ONE_HUNDRED = new BigDecimal(100);

  private Integer id;
  private Timestamp stamp;
  private Budget budget;
  private String name;
  private Category category;
  private BigDecimal allocation = new BigDecimal(0.0);
  private AllocationType allocationType;
  private Boolean autoDeduct;

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
  @Column(name = "stamp", nullable = false, secondPrecision = 3)
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

  @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  @Fetch(value = FetchMode.SELECT)
  @JoinColumn(name = "category")
  public Category getCategory() {
    return category;
  }

  public void setCategory(Category category) {
    this.category = category;
  }

  @Column(name = "allocation", nullable = false)
  public BigDecimal getAllocation() {
    return allocation;
  }

  public void setAllocation(BigDecimal allocation) {
    this.allocation = allocation;
  }

  // the column is allocation_type, not type -- mapping it as "type" meant every
  // query for a preset failed with Unknown column 'this_.type', so nothing about
  // presets had ever run
  @Column(name = "allocation_type", nullable = false)
  @Enumerated(value = jakarta.persistence.EnumType.STRING)
  public AllocationType getAllocationType() {
    return allocationType;
  }

  public void setAllocationType(AllocationType allocationType) {
    this.allocationType = allocationType;
  }

  @Column(name = "auto_deduct", nullable = false)
  public Boolean isAutoDeduct() {
    return autoDeduct;
  }

  public void setAutoDeduct(Boolean autoDeduct) {
    this.autoDeduct = autoDeduct;
  }

  /**
   * What this row puts on a transaction of the given amount: a fixed row its own
   * figure, a percent row that share of the amount.  A percentage is a whole
   * number carried to many places (7.60764626375748 means 7.6%), so the product is
   * taken at full precision and only the result brought to the cent.
   *
   * <p>Action.applyAllocationPreset lays rows down with this and the preset
   * editor totals them with it, so the editor's total is the sum the server will
   * produce.  Not a getter, so Hibernate leaves it alone.
   */
  public Money valueFor(Money gross) {
    BigDecimal rate = allocation == null ? BigDecimal.ZERO : allocation;
    if (allocationType != AllocationType.percent)
      return new Money(rate.setScale(2, RoundingMode.HALF_UP));
    BigDecimal base = gross == null ? BigDecimal.ZERO : gross.toBigDecimal();
    return new Money(base.multiply(rate).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP));
  }
}
