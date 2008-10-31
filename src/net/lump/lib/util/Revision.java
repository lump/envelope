package us.lump.lib.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.TimeZone;
import java.text.SimpleDateFormat;
import java.text.ParseException;

/**
 * Utility class for enum access to cvs variables for this file.
 *
 * @author Troy Bowman
 * @version $Id: Revision.java,v 1.5 2008/10/31 19:10:09 troy Test $
 */
public enum Revision {

  Name,
  State,
  Revision,
  Date,
  Author;

  // store the cleaned-up value from which cvs gives us.
  private String value = null;
  
  // get CVS to fill in the values in strings
  private final String[] REVS = new String[]{
      "$Name:  $",
      "$State: Test $",
      "$Revision: 1.5 $",
      "$Date: 2008/10/31 19:10:09 $",
      "$Author: troy $"
  };

  /** the date format for Date */
  public static final String dateFormatString = "yyyy/MM/dd HH:mm:ss";
  /** a simple date formatter which uses dateFormatString */
  public static final SimpleDateFormat dateFormat;
  /** a pattern to tell if the value is formatted like a date */
  public static final Pattern datePattern =
      Pattern.compile("^\\d{4}(?:/\\d{2}){2}\\s\\d{2}(?:\\:\\d{2}){2}$");

  static {
    dateFormat = new SimpleDateFormat(dateFormatString);
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  private Revision() {
    Pattern p =
        Pattern.compile("^\\$" + this.toString() + ":\\s(\\S.+)\\s\\$$");
    Matcher m = p.matcher(REVS[ordinal()]);
    if (m.matches()) value = m.group(1);
  }

  /**
   * Is this entry a date?
   * 
   * @return boolean
   */
  public boolean isDate() {
    return value != null && datePattern.matcher(value).matches();
  }

  /**
   * Get a java.util.Date if this is a date.
   * @return Date
   * @throws IllegalStateException if this isn't a date.
   */
  public java.util.Date getDate() {
    java.util.Date date = null;
    if (isDate()) try {
      date = dateFormat.parse(value);
    } catch (ParseException e) {
      // nevermind
    }

    if (date == null) throw new IllegalStateException(value + " is not a date");

    return date;
  }

  /**
   * Retrieve the String value.
   *
   * @return String
   */
  public String value() {
    return value;
  }

  /**
   * Return a prettyValue of name, or if it's null, the value of state.
   * @return String
   */
  public static String nameOrState() {
    return Name.value() != null
    ? Name.prettyValue()
    : State.value();
  }
  
  /**
   * Return the value with the underscores removed.
   *
   * @return String
   */
  public String prettyValue() {
    if (value == null) return null;
    String out = value;

    // replace underscores between numbers with a dot.
    while (out.matches(".*\\d_\\d.*"))
      out = out.replaceAll("(\\d)_(\\d)", "$1.$2");

    // replace all other ounderscores with a space.
    out = out.replaceAll("_", " ");
    return out;
  }
}
