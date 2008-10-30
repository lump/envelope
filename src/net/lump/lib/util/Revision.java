package us.lump.lib.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for programmatic access to cvs variables.
 *
 * @author Troy Bowman
 * @version $Id: Revision.java,v 1.1 2008/10/30 21:30:33 troy Exp $
 */
public class Revision {

  private static final String[] REVS = new String[]{
      "$Name:  $",
      "$State: Exp $",
  };

  public String getRevision() {
    String version = null;

    Pattern p = Pattern.compile("^\\$(Name|State|Revision):\\s+(.+?)\\s*$");

    for (String s : REVS) {
      Matcher m = p.matcher(s);
      if (m.matches()) {
        version = m.group(1);
        break;
      }
    }

    return version;
  }
}
