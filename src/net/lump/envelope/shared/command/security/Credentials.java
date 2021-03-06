package net.lump.envelope.shared.command.security;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Represents credentials provided by the client.  This object is used during a session with a client to simplify authentication for
 * each request.
 *
 * @author Troy Bowman
 * @version $Id: Credentials.java,v 1.2 2009/10/02 22:06:23 troy Exp $
 */
public class Credentials implements Serializable {
  private String username;
  private String signature;
  private Long stamp;

  /**
   * A new credentials object.
   *
   * @param username obviously, the username
   */
  public Credentials(String username) {
    this.username = username;
    this.stamp = System.currentTimeMillis();
  }

  /**
   * Get the signature.
   *
   * @return String
   */
  public String getSignature() {
    return signature;
  }

  /**
   * Set the singature
   *
   * @param signature should usually be a signature of the command
   */
  public void setSignature(String signature) {
    this.signature = signature;
  }

  /**
   * Returns the timestamp for when this credentials instance was created.
   *
   * @return Long
   */
  public Long getStamp() {
    return this.stamp;
  }

  /**
   * Returns the username.
   *
   * @return String
   */
  public String getUsername() {
    return username;
  }

  /**
   * Sets the username.
   *
   * @param username the username.
   */
  public void setUsername(String username) {
    this.username = username;
  }

  public String toString() {
    return SimpleDateFormat.getInstance().format(new Date(stamp));
  }

  /**
   * Generate a hashcode of this object, without including the singature, since in many cases the signature will include this
   * object.
   *
   * @return int
   */
  public int noSignatureHashCode() {
    int result;
    result = (username != null ? username.hashCode() : 0);
    return result;
  }

  @SuppressWarnings({"SimplifiableIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Credentials that = (Credentials)o;

    if (signature != null
      ? !signature.equals(that.signature)
      : that.signature != null) return false;
    return !(username != null
      ? !username.equals(that.username)
      : that.username != null);

  }

  public int hashCode() {
    int result;
    result = (username != null ? username.hashCode() : 0);
    result = 31 * result + (signature != null ? signature.hashCode() : 0);
    return result;
  }
}
