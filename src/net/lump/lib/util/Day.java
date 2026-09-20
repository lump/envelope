package net.lump.lib.util;

import java.sql.Date;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * A calendar day carried as a java.sql.Date, without the zone getting a vote.
 *
 * <p>Transaction.date is a calendar day -- "August 31st", the same day for
 * everyone -- but java.sql.Date is an instant, and the day an instant falls
 * on depends on a zone.  The server stores whatever UTC day the instant it
 * receives falls on, and reads a stored day back as midnight UTC; so the one
 * representation both ends agree on is <em>midnight UTC of the day</em>.
 * Everything here converts between that and the user's own view of the day.
 *
 * <p>The mistake this replaces: the client made the instant at midnight in
 * the <em>user's</em> zone.  For a user west of Greenwich that instant was
 * still the same UTC day, so the store was right -- but the read came back as
 * midnight UTC, which was the evening before in the user's zone, and the
 * chooser showed the day before.  East of Greenwich the store was wrong
 * instead.  Either way, a day lost per round trip, and the form's save-as-you-
 * edit then wrote the lost day back.
 */
public final class Day {

  private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

  private Day() {}

  /** The wire form of a calendar day: midnight UTC. */
  public static Date of(int year, int month1to12, int day) {
    Calendar c = new GregorianCalendar(UTC);
    c.clear();
    c.set(year, month1to12 - 1, day, 0, 0, 0);
    return new Date(c.getTimeInMillis());
  }

  /**
   * The calendar day a widget's Date means, in the user's zone, carried as
   * midnight UTC.  Use on the way OUT: chooser to entity.
   *
   * @param picked what a java.util.Date widget produced -- midnight, or any
   *               time, of the day the user chose, in the user's zone
   * @param zone   the user's zone
   */
  public static Date fromView(java.util.Date picked, TimeZone zone) {
    if (picked == null) return null;
    Calendar c = new GregorianCalendar(zone);
    c.setTime(picked);
    return of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
  }

  /**
   * A stored day, arriving as midnight UTC, as a Date a widget in the user's
   * zone will show as that same day.  Use on the way IN: entity to chooser.
   *
   * @param stored the entity's date -- midnight UTC of the day
   * @param zone   the user's zone
   */
  public static java.util.Date toView(Date stored, TimeZone zone) {
    if (stored == null) return null;
    Calendar utc = new GregorianCalendar(UTC);
    utc.setTime(stored);
    Calendar c = new GregorianCalendar(zone);
    c.clear();
    c.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
    return c.getTime();
  }

  /** Today, as a wire-form day, in the user's zone. */
  public static Date today(TimeZone zone) {
    return fromView(new java.util.Date(), zone);
  }

  /**
   * A stored day spelled the way the database spells it, {@code yyyy-MM-dd} of
   * its UTC day.  This is the form to log or compare -- never
   * {@code toString()}, which renders in the default zone and so reads a day
   * early west of Greenwich for a value that is perfectly correct.  The
   * readiness probe leans on it to check the day Hibernate read back against
   * the day the database says it holds.
   *
   * @param stored the entity's date -- midnight UTC of the day
   */
  public static String toIso(java.util.Date stored) {
    if (stored == null) return null;
    Calendar utc = new GregorianCalendar(UTC);
    utc.setTime(stored);
    return String.format("%04d-%02d-%02d",
        utc.get(Calendar.YEAR), utc.get(Calendar.MONTH) + 1, utc.get(Calendar.DAY_OF_MONTH));
  }
}
