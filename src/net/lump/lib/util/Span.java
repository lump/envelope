package us.lump.lib.util;

/**
 * A Span is a utility class used to define intervals of time.
 *
 * @author troy
 * @version $Id: Span.java,v 1.1 2007/08/21 04:09:35 troy Exp $
 */
public class Span {

  /** A day in milliseconds */
  public static final Span DAY = new Span("d", 86400000L);
  /** An hour in milliseconds */
  public static final Span HOUR = new Span("h", 3600000L);
  /** A minute in milliseconds */
  public static final Span MINUTE = new Span("m", 60000L);
  /** A second in milliseconds */
  public static final Span SECOND = new Span("s", 1000L);
  /** An array of Span, including DAY, HOUR, MINUTE, SECOND */
  public static final Span[] DHMS =
      new Span[]{Span.DAY, Span.HOUR, Span.MINUTE, Span.SECOND};

  /** the abbreviation name */
  public String abbr;
  /** the time in milliseconds */
  public long millis;

  /**
   * Construct a new span providing the abbreviation and milliseconds.
   *
   * @param abbreviation name
   * @param milliseconds of time
   */
  Span(String abbreviation, long milliseconds) {
    abbr = abbreviation;
    millis = milliseconds;
  }

  /**
   * Returns a string which describes the interval, delimited by "d", "h", "m",
   * "s", or "ms", all corresponding to the different units of time.
   *
   * @param start the start system time
   * @param end   the end system time
   *
   * @return String
   */
  public static String interval(long start, long end) {

    String out = "";
    long interval = (end - start);

    for (Span s : Span.DHMS) {
      long unit = (interval - (interval % s.millis)) / s.millis;
      interval -= unit * s.millis;
      if (unit > 0L) {
        out += String.valueOf(unit) + s.abbr;
      }
    }
    if (interval > 0L) out += interval + "ms";

    return out;
  }
}
