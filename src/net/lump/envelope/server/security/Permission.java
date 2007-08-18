package us.lump.envelope.server.security;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * An object which runs bitwise manipulations to determine access levels.
 *
 * @author Troy Bowman
 * @version $Id: Permission.java,v 1.2 2007/08/18 23:20:11 troy Exp $
 */
public class Permission implements Serializable {
  public static final long READ = 1L;
  public static final long WRITE = 2L;
  public static final long ADMIN = 4L;
  public static final HashMap<Long, String> list = new HashMap<Long, String>();
  private long permission;

  static {
    list.put(1L, "READ");
    list.put(2L, "WRITE");
    list.put(4L, "ADMIN");
  }

  /**
   * Create a Permission from a Long
   *
   * @param permission the long
   */
  public Permission(Long permission) {
    this.permission = permission;
  }

  /**
   * Create a new Permission object from a different one.
   *
   * @param that the other Permission object.
   */
  public Permission(Permission that) {
    this.permission = that.permission;
  }

  /** Create a Permsision object with no permission flags set. */
  public Permission() {
    permission = 0L;
  }

  /**
   * Checks to see if any of the bits in the provided int exist in this object.
   *
   * @param permission the Long for testing
   *
   * @return boolean
   */
  public boolean hasAnyPermission(Long permission) {
    return (this.permission & permission) > 0L;
  }

  /**
   * Checks to see if the all of the bits in the provided Integer exist in this
   * object.
   *
   * @param permission the Long for testing
   *
   * @return boolean true if they all exist, false if any do not
   */
  public boolean hasPermission(Long permission) {
    return (this.permission & permission) == permission;
  }

  /**
   * Removes (turns off) all bits in this object that are turned on in the
   * provided Integer.
   *
   * @param permission the Long which contains the bit(s) which will bet turned
   *                   off
   */
  public void removePermission(Long permission) {
    long temp = this.permission;
    temp ^= permission;
    this.permission &= temp;
  }

  /**
   * Sets the long which describes the permissions obeject to the provided
   * Long.
   *
   * @param permission the provided long
   */
  public void setLong(Long permission) {
    this.permission = permission;
  }

  /**
   * This basically clones a Permission object.
   *
   * @param permission the other permission.
   */
  public void setPermissions(Permission permission) {
    this.permission = permission.permission;
  }

  /**
   * Sets all of the permissions in this object to exactly what is contained in
   * the Long that is provided.
   *
   * @param permission the provided Long.
   */
  public void setPermissions(Long permission) {
    this.permission = permission;
  }

  /**
   * Returns the Long which describes the bits of this permission.
   *
   * @return Long
   */
  public Long toLong() {
    return permission;
  }

  /**
   * Toggles all bits on/off in this object that are turned on in the provided
   * Long.
   *
   * @param permission the long which contains the bits to be manipulated.
   */
  public void togglePermission(Long permission) {
    this.permission ^= permission;
  }

  public String toString() {
    StringBuilder out = new StringBuilder();
    ArrayList<Long> keys = new ArrayList<Long>(list.keySet());
    Collections.sort(keys);
    for (long l : keys) {
      out.append(list.get(l));
      if (l != keys.get(keys.size() - 1))
        out.append(",");
    }

    return out.toString();
  }
}
