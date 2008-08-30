package us.lump.envelope.server;

/**
 * Bitwise flags for transfer options.
 *
 * @version $Id: XferFlags.java,v 1.1 2008/08/30 22:06:34 troy Exp $
 */
public class XferFlags {

  public static final byte LIST = 1; // 1
  public static final byte COMPRESS = (1 << 1) & 0xff; // 2
  public static final byte ENCRYPT = (1 << 2) & 0xff; // 4
  private static final byte RESERVED1 = (1 << 3) & 0xff; // 8
  private static final byte RESERVED2 = (1 << 4) & 0xff; // 16
  private static final byte RESERVED3 = (1 << 5) & 0xff; // 32
  private static final byte RESERVED4 = (1 << 6) & 0xff; // 64
  
  private byte flags = 0;

  public XferFlags() {
    this((byte)0);
  }

  public XferFlags(byte flags) {
    this.flags = flags;
  }


  /**
   * Adds all turned-on bits in the provided byte to this object.
   *
   * @param permission the flag(s) to add
   */
  public void addFlag(byte permission) {
    this.flags |= permission;
  }

  /**
   * Returns the permissions in this object as a byte.
   *
   * @return byte
   */
  public byte getByte() {
    return flags;
  }

  /**
   * Checks to see if any of the bits in the provided int exist in this
   * object.
   *
   * @param permission the permission(s) to check
   *
   * @return boolean
   */
  public boolean hasAnyFlag(byte permission) {
    return (this.flags & permission) > 0;
  }

  /**
   * Checks to see if the all of the bits in the provided byte exist in this
   * object.
   *
   * @param permission the permission to check
   *
   * @return boolean
   */
  public boolean hasFlag(byte permission) {
    return (this.flags & permission) == permission;
  }

  /**
   * Removes (turns off) all bits in this object that are turned on in the
   * provided byte.
   *
   * @param permission bits to remove
   */
  public void removeFlag(byte permission) {
    this.flags &= this.flags ^ permission;
  }

  /**
   * This basically clones a Flags object.
   *
   * @param permission the object to clone
   */
  public void setFlags(XferFlags permission) {
    this.flags = permission.flags;
  }

  /**
   * Sets all of the permissions in this object to exactly what is contained in
   * the byte that is provided.
   *
   * @param permission to set
   */
  public void setFlags(byte permission) {
    this.flags = permission;
  }

  /**
   * Toggles all bits on/off in this object that are turned on in the provided
   * byte.
   *
   * @param permission to toggle
   */
  public void toggleFlag(byte permission) {
    this.flags ^= permission;
  }
}
