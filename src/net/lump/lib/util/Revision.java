package us.lump.lib.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for enum access to cvs variables.
 *
 * @author Troy Bowman
 * @version $Id: Revision.java,v 1.2 2008/10/30 21:46:41 troy Exp $
 */
public enum Revision {

  Name,
  State,
  Revision,
  Date,
  Author;

  // store the value from which cvs gives us.
  private String value = null;

  // get CVS to fill in the values in strings
  private final String[] REVS = new String[]{
      "$Name:  $",
      "$State: Exp $",
      "$Revision: 1.2 $",
      "$Date: 2008/10/30 21:46:41 $",
      "$Author: troy $"
  };

  private Revision() {
    Pattern p = Pattern.compile("^\\$" + this.toString() + ":\\s+(.+?)\\s*$");
    Matcher m = p.matcher(REVS[ordinal()]);
    if (m.matches()) value = m.group(1);
  }

  /**
   * Retrieve the String value.
   *
   * @return String
   */
  public String value() {
    return value;
  }
}
