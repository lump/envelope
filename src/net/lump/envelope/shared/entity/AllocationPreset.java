package us.lump.envelope.shared.entity;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import javax.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * A record which maintains an allocation preset.
 *
 * @author troy
 * @version $Id: AllocationPreset.java,v 1.1 2009/07/13 17:21:44 troy Exp $
 */
@javax.persistence.Entity
@Table(name = "allocation_presets")
public class AllocationPreset extends Identifiable<Integer, Timestamp> implements Stampable<Timestamp> {
  public static enum AllocationType {
    percent,
    fixed,
  }

  private Integer id;
  private Timestamp stamp;
  private Budget budget;
  private String name;
  private Category category;
  private BigDecimal allocation = new BigDecimal(0.0);
  private AllocationType allocationType;
  private Boolean autoDeduct;

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

  @Column(name = "type", nullable = false)
  @Enumerated(value = javax.persistence.EnumType.STRING)
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
}
