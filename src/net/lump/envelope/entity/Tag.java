package us.lump.envelope.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * A tag.  This allows an allocation to be tagged with several identifiers, for
 * use in querying what has been spent for aspects of allocations, rather than a
 * general category, and which can span different categories.  For example, a
 * "Odyssey Van" tag will have charges in any number of categories like
 * Gasoline, Car Payment, Car Maintenance, Car Insurance, etc.  To find out what
 * has been spent on the Odyssey Van, one would be able to query all allocations
 * tagged with it.  Also, an Allocation may have multiple tags, since an
 * allocation to the Car Insurance budget Category may dedicate to more than one
 * car.
 *
 * @author troy
 * @version $Id: Tag.java,v 1.2 2008/05/13 01:25:31 troy Exp $
 */
@javax.persistence.Entity
@org.hibernate.annotations.Entity(dynamicUpdate = true)
@Table(name = "tags")
public class Tag extends Identifiable {
  private Integer id;
  private Timestamp stamp;
  private String name;
  private Budget budget;

  public String toString() {
    return name;
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

  @Column(name = "name", nullable = false, length = 64)
  public String getName() {
    return name;
  }

  public void setName(String tag) {
    name = tag;
  }

  /**
   * Tags are associated with a budget.  This is basically a shortcut, instead
   * of doing a huge relational query of tag->allocation->category->account->budget
   * just to find out the tags for a budget.
   *
   * @return Budget
   */
  @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  @JoinColumn(name = "budget")
  public Budget getBudget() {
    return budget;
  }

  public void setBudget(Budget budget) {
    this.budget = budget;
  }

  @SuppressWarnings({"RedundantIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Tag tag = (Tag)o;

    if (id != null ? !id.equals(tag.id) : tag.id != null) return false;
    if (name != null ? !name.equals(tag.name) : tag.name != null) return false;
    if (stamp != null ? !stamp.equals(tag.stamp) : tag.stamp != null)
      return false;
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
