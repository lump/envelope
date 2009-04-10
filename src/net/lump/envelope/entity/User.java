package us.lump.envelope.entity;

import us.lump.envelope.command.security.Permission;

import javax.persistence.*;
import java.security.PublicKey;
import java.sql.Timestamp;
import java.text.MessageFormat;


/**
 * User.
 *
 * @author Troy Bowman
 * @version $Id: User.java,v 1.7 2009/04/10 22:49:28 troy Exp $
 */
@javax.persistence.Entity
@Table(name = "users")
public class User extends Identifiable<Integer, Timestamp> {
  private Integer id;
  private Timestamp stamp;
  private Budget budget;
  private String name;
  private String realName;
  private String cryptPassword;
  private Permission permissions;
  private PublicKey publicKey;

  public String toString() {
    return MessageFormat.format("{0} ({1})", name, realName);
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

  @Column(name = "crypt_password")
  public String getCryptPassword() {
    return cryptPassword;
  }

  public void setCryptPassword(String cryptPassword) {
    this.cryptPassword = cryptPassword;
  }

  @Column(name = "public_key")
  @Lob
  public PublicKey getPublicKey() {
    return publicKey;
  }

  public void setPublicKey(PublicKey publicKey) {
    this.publicKey = publicKey;
  }

  @Column(name = "real_name")
  public String getRealName() {
    return realName;
  }

  public void setRealName(String realName) {
    this.realName = realName;
  }

  @Transient
  public Permission getPermission() {
    return permissions;
  }

  @Column(name = "permissions")
  public Long getPermissions() {
    return permissions.toLong();
  }

  public void setPermission(Permission permission) {
    permissions = permission;
  }

  public void setPermissions(Long permissions) {
    this.permissions = new Permission(permissions);
  }

  @SuppressWarnings({"RedundantIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    User user = (User)o;

    if (budget != null
      ? !budget.equals(user.budget)
      : user.budget != null) return false;
    if (cryptPassword != null
      ? !cryptPassword.equals(user.cryptPassword)
      : user.cryptPassword != null) return false;
    if (id != null
      ? !id.equals(user.id)
      : user.id != null) return false;
    if (name != null
      ? !name.equals(user.name)
      : user.name != null) return false;
    if (permissions != null
      ? !permissions.equals(user.permissions)
      : user.permissions != null) return false;
    if (publicKey != null
      ? !publicKey.equals(user.publicKey)
      : user.publicKey != null) return false;
    if (realName != null
      ? !realName.equals(user.realName)
      : user.realName != null) return false;
    if (stamp != null
      ? !stamp.equals(user.stamp)
      : user.stamp != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (id != null ? id.hashCode() : 0);
    result = 31 * result + (stamp != null ? stamp.hashCode() : 0);
    result = 31 * result + (budget != null ? budget.hashCode() : 0);
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (realName != null ? realName.hashCode() : 0);
    result = 31 * result + (cryptPassword != null
      ? cryptPassword.hashCode()
      : 0);
    result = 31 * result + (permissions != null ? permissions.hashCode() : 0);
    result = 31 * result + (publicKey != null ? publicKey.hashCode() : 0);
    return result;
  }
}
