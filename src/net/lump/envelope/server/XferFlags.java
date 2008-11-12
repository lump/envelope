package us.lump.envelope.server;

/**
 * Bitwise flags for transfer options.
 *
 * @version $Id: XferFlags.java,v 1.4 2008/11/12 18:15:17 troy Exp $
 */
public class XferFlags {

  public enum Flag {
    F_NONE,
    F_ENCRYPT,
    F_COMPRESS,
    F_OBJECT,
    F_LIST,
    F_LISTS,
//    F_RESERVED1,
//    F_RESERVED2,
    ;

    private byte flag = 0;

    private Flag() {
      if (ordinal() > 0) flag = (byte)(1 << (ordinal() - 1));
    }

    public byte bit() { return flag; }
  }

  private byte flags = Flag.F_NONE.bit();

  /**
   * Creates a new XferFlags from the list of flags provided.
   *
   * @param flags to use
   */
  public XferFlags(Flag... flags) {
    if (flags.length > 0) add(flags);
  }

  /**
   * Creates a new XferFlags from a byte.
   *
   * @param flags byte to use
   */
  public XferFlags(byte flags) {
    this.flags = flags;
  }

  /**
   * ORs all turned-on bits in the provided flags to this object.
   *
   * @param flags the flag(s) to add
   */
  public void add(Flag... flags) {
    if (flags.length == 0) return;
    this.flags |= addFlagsTogether(flags);
  }

  /**
   * OR some Flags together into a byte;
   *
   * @param flags to add together
   *
   * @return byte
   */
  private static byte addFlagsTogether(Flag... flags) {
    byte ored = 0;
    for (Flag f : flags) ored |= f.bit();
    return ored;
  }

  /**
   * Returns the flags in this object as a byte.
   *
   * @return byte
   */
  public byte getByte() {
    return flags;
  }

  /**
   * Checks to see if any of the bits in the provided Flags exist in this
   * object.
   *
   * @param flags: one or more flags
   *
   * @return boolean
   */
  public boolean hasAny(Flag... flags) {
    if (flags.length == 0) return this.flags == 0;
    else if ((this.flags & addFlagsTogether(flags)) > 0) return true;
    return false;
  }

  /**
   * Checks to see if the all of the bits in the provided flags exist in this
   * object.
   *
   * @param flags the flags to compare
   *
   * @return boolean
   */
  public boolean has(Flag... flags) {
    if (flags.length == 0) return this.flags == 0;
    else {
      byte that = addFlagsTogether(flags);
      return ((this.flags & that) == that);
    }
  }

  /**
   * Removes (turns off) all bits in this object that are turned on in the
   * provided byte.
   *
   * @param flags to remove
   */
  public void remove(Flag... flags) {
    this.flags &= this.flags ^ addFlagsTogether(flags);
  }

  /**
   * This basically clones a Flags object.
   *
   * @param flags the object to clone
   */
  public void set(XferFlags flags) {
    this.flags = flags.flags;
  }

  /**
   * Sets all of the flags in this object to exactly what is contained in the
   * byte that is provided.
   *
   * @param flags to set
   */
  public void set(byte flags) {
    this.flags = flags;
  }

  /**
   * Toggles all bits on/off in this object that are turned on in the provided
   * byte.
   *
   * @param flags to toggle
   */
  public void toggle(Flag... flags) {
    this.flags ^= addFlagsTogether(flags);
  }

  /**
   * Returns a comma-delimited list representing the flags of this instance.
   *
   * @return String
   */
  public String toString() {
    if (this.flags == 0) return Flag.F_NONE.toString();
    String out = "";
    for (Flag f : Flag.values()) {
      if (f.bit() == 0) continue;
      if (this.has(f)) {
        if (out.length() > 0) out += ",";
        out += f.toString();
      }
    }
    return out;
  }
}
