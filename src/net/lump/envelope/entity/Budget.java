package us.lump.envelope.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * A budget object.
 *
 * @author Troy Bowman
 * @version $Id: Budget.java,v 1.5 2008/07/09 04:20:02 troy Test $
 */
@javax.persistence.Entity
@Table(name = "budgets")
public class Budget extends Identifiable<Integer, Timestamp> {
  private Integer id;
  private Timestamp stamp;
  private String name;

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
    this.id = (Integer)id;
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
   * Returns the Budget name.
   *
   * @return String
   */
  @Column(name = "name", nullable = false, length = 64)
  public String getName() {
    return name;
  }

  /**
   * Sets the Budget name.
   *
   * @param name String
   */
  public void setName(String name) {
    this.name = name;
  }

  @SuppressWarnings({"RedundantIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Budget budget = (Budget)o;

    if (id != null
        ? !id.equals(budget.id)
        : budget.id != null) return false;
    if (name != null
        ? !name.equals(budget.name)
        : budget.name != null) return false;
    if (stamp != null
        ? !stamp.equals(budget.stamp)
        : budget.stamp != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (id != null ? id.hashCode() : 0);
    result = 31 * result + (stamp != null ? stamp.hashCode() : 0);
    result = 31 * result + (name != null ? name.hashCode() : 0);
    return result;
  }
}
