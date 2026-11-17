package net.lump.envelope.shared.entity;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import net.lump.envelope.shared.command.security.Permission;
import net.lump.lib.util.Encryption;

import jakarta.persistence.*;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.sql.Timestamp;
import java.text.MessageFormat;


/**
 * User.
 *
 * @author Troy Bowman
 */
@jakarta.persistence.Entity
@Table(name = "users")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class User extends Identifiable<Integer, Timestamp> {
  private Integer id;
  private Timestamp stamp;
  private Budget budget;
  private String name;
  private String realName;
  private String md5Salt;
  private byte[] scramSalt;
  private Integer scramIterations;
  private byte[] scramStoredKey;
  private byte[] scramServerKey;
  private Permission permissions;
  private String publicKey;

  public String toString() {
    return MessageFormat.format("{0} ({1})", name, realName);
  }

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id", nullable = false) @Override
  public Integer getId() {
    return id;
  }

  @Override
  public void setId(Integer id) {
    this.id = id;
  }

  @Version @Column(name = "stamp", nullable = false, secondPrecision = 3) @Override
  public Timestamp getStamp() {
    return stamp;
  }

  public void setStamp(Timestamp stamp) {
    this.stamp = stamp;
  }

  @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE}) @JoinColumn(name = "budget")
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

  // The SCRAM-SHA-256 verifier (see Scram).  None of these can be used to log in:
  // StoredKey only checks a proof, ServerKey only signs the server's answer, and
  // the salts and count are public.  They never leave the server -- forClient
  // builds the client's copy without them.

  /** the md5-crypt salt the client pre-hashes the password under */
  @Column(name = "md5_salt", length = 12)
  public String getMd5Salt() { return md5Salt; }
  public void setMd5Salt(String md5Salt) { this.md5Salt = md5Salt; }

  @Column(name = "scram_salt", length = 32)
  public byte[] getScramSalt() { return scramSalt; }
  public void setScramSalt(byte[] scramSalt) { this.scramSalt = scramSalt; }

  @Column(name = "scram_iterations")
  public Integer getScramIterations() { return scramIterations; }
  public void setScramIterations(Integer scramIterations) { this.scramIterations = scramIterations; }

  @Column(name = "scram_stored_key", length = 32)
  public byte[] getScramStoredKey() { return scramStoredKey; }
  public void setScramStoredKey(byte[] scramStoredKey) { this.scramStoredKey = scramStoredKey; }

  @Column(name = "scram_server_key", length = 32)
  public byte[] getScramServerKey() { return scramServerKey; }
  public void setScramServerKey(byte[] scramServerKey) { this.scramServerKey = scramServerKey; }

  /** Put a verifier on this user: the only way a password is set. */
  public void setVerifier(net.lump.envelope.shared.command.security.Scram.Verifier v) {
    setMd5Salt(v.md5Salt);
    setScramSalt(v.salt);
    setScramIterations(v.iterations);
    setScramStoredKey(v.storedKey);
    setScramServerKey(v.serverKey);
  }


  @Column(name = "public_key")
  public String getPublicKeyString() {
    return publicKey;
  }

  public void setPublicKeyString(String publicKey) {
    this.publicKey = publicKey;
  }

  @Transient
  public PublicKey getPublicKey() throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
    return Encryption.decodePublicKey(publicKey);
  }

  public void setPublicKey(PublicKey publicKey) {
    this.publicKey = Encryption.encodeKey(publicKey);
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

    if (budget != null ? !budget.equals(user.budget) : user.budget != null) return false;
    if (id != null ? !id.equals(user.id) : user.id != null) return false;
    if (name != null ? !name.equals(user.name) : user.name != null) return false;
    if (permissions != null ? !permissions.equals(user.permissions) : user.permissions != null) return false;
    if (publicKey != null ? !publicKey.equals(user.publicKey) : user.publicKey != null) return false;
    if (realName != null ? !realName.equals(user.realName) : user.realName != null) return false;
    if (stamp != null ? !stamp.equals(user.stamp) : user.stamp != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (id != null ? id.hashCode() : 0);
    result = 31 * result + (stamp != null ? stamp.hashCode() : 0);
    result = 31 * result + (budget != null ? budget.hashCode() : 0);
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (realName != null ? realName.hashCode() : 0);
    result = 31 * result + (permissions != null ? permissions.hashCode() : 0);
    result = 31 * result + (publicKey != null ? publicKey.hashCode() : 0);
    return result;
  }
}
