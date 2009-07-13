package us.lump.envelope.shared.entity;

import javax.persistence.*;
import java.sql.Timestamp;

/** An entity to or from which we do transactions with. */
@javax.persistence.Entity
@org.hibernate.annotations.Entity(dynamicUpdate = true)
@Table(name = "entities")
@org.hibernate.annotations.Table(appliesTo = "entities")
public class Entity extends Identifiable<Integer, Timestamp> {

  private Integer id;
  private Timestamp stamp;
  private Budget budget;
  private String name;
  private String address;
  private String city;
  private String state;
  private String zip;
  private String phone;

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
  @Column(nullable = false)
  @Override
  public Timestamp getStamp() {
    return stamp;
  }

  public void setStamp(Timestamp stamp) {
    this.stamp = stamp;
  }

  @Column(nullable = false)
  public Budget getBudget() {
    return budget;
  }


  public void setBudget(Budget budget) {
    this.budget = budget;
  }

  @Column(nullable = false)
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Column(nullable = false)
  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  @Column(nullable = false)
  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  @Column(nullable = false)
  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  @Column(nullable = false)
  public String getZip() {
    return zip;
  }

  public void setZip(String zip) {
    this.zip = zip;
  }

  @Column(nullable = false)
  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }
}
