package us.lump.lib.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for enum access to cvs variables for this file.
 *
 * @author Troy Bowman
 * @version $Id: Revision.java,v 1.6 2009/03/17 23:52:12 troy Exp $
 */
public enum Revision {

  /** The login name of the user who checked in the revision.*/
  Author,
  /** The name of the branch that the revision is a member of. */
  Branch,
  /** The Commit (or Session) identifier of the commit that generated this revision. */
  CommitId,
  /** The date and time (UTC) the revision was checked in. */
  Date,
  /** A standard header containing the full pathname of the rcs file, the revision number, the date (UTC), the author, the state, and the locker (if locked). Files will normally never be locked when you use cvsnt. */
  Header,
  /** A standard header containing the relative pathname of the rcs file, the revision number, the date (UTC), the author, the state, and the locker (if locked). Files will normally never be locked when you use cvsnt. */
  RCSHeader,
  /** Same as $Header: /export/cvsroot/envelope/src/net/lump/lib/util/Revision.java,v 1.6 2009/03/17 23:52:12 troy Exp $, except that the rcs filename is without a path. */
  Id,
  /** Tag name used to check out this file. The keyword is expanded only if one checks out with an explicit tag name. For example, when running the command cvs co -r first, the keyword expands to Name: first. */
  Name,
  /** The login name of the user who locked the revision (empty if not locked, which is the normal case unless cvs admin -l is in use). This keyword has little meaning under cvsnt.*/
  Locker,
//  /* The log message supplied during commit, preceded by a header containing the rcs filename, the revision number, the author, and the date (UTC). Existing log messages are not replaced. Instead, the new log message is inserted after $Log$. Each new line is prefixed with the same string which precedes the $Log keyword. For example, if the file contains */
// we can't parse log yet  
//  Log,
  /** The name of the rcs file without a path. */
  RCSfile,
  /** The revision number assigned to the revision. */
  Revision,
  /** The full pathname of the rcs file. */
  Source,
  /** The state assigned to the revision. States can be assigned with cvs admin. */
  State;



  // store the cleaned-up value from which cvs gives us.
  private String value = null;
  
  // get CVS to fill in the values in strings
  private final String[] REVS = new String[]{
      "$Author: troy $",
      "$Branch$",
      "$CommitId$",
      "$Date: 2009/03/17 23:52:12 $",
      "$Header: /export/cvsroot/envelope/src/net/lump/lib/util/Revision.java,v 1.6 2009/03/17 23:52:12 troy Exp $",
      "$RCSHeader$",
      "$Id: Revision.java,v 1.6 2009/03/17 23:52:12 troy Exp $",
      "$Name:  $",
      "$Locker:  $",
//      "$Log: Revision.java,v $
//      "Revision 1.6  2009/03/17 23:52:12  troy
//      "all variables
//      "",  // we can't parse log yet
      "$RCSFile$",
      "$Revision",
      "$Source: /export/cvsroot/envelope/src/net/lump/lib/util/Revision.java,v $",
      "$State: Exp $",
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
