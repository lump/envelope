package net.lump.envelope.client;

import com.toedter.calendar.JDateChooser;
import com.toedter.calendar.JTextFieldDateEditor;
import junit.framework.TestCase;
import net.lump.envelope.client.ui.components.Hierarchy;
import net.lump.envelope.client.ui.components.ZonedDateEditor;
import net.lump.envelope.client.ui.components.forms.transaction.DayRenderer;
import net.lump.envelope.shared.entity.Account;
import net.lump.lib.Money;
import net.lump.lib.util.Day;
import org.hibernate.criterion.DetachedCriteria;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Every place a date crosses a boundary in this application.
 *
 * <p>{@code Transaction.date} is a <em>calendar day</em> -- "August 31st", the
 * same day for everyone -- but it is carried as a {@code java.sql.Date}, which
 * is an instant, and the day an instant falls on depends on who is looking. Each
 * boundary that conversion crosses has had a day-off-by-one bug in it at some
 * point: the store, the read, the table column, the date picker, and the picker
 * again when the zone preference changes underneath it. This pins all of them.
 *
 * <p>Needs no server and no database, so it runs on its own.
 *
 * @author Troy Bowman
 */
public class TestDates extends TestCase {

  /**
   * Deliberately spread either side of UTC and off the whole hour. Whole classes
   * of this bug are invisible in one hemisphere: the original store/read
   * mismatch lost a day west of Greenwich on the read and east of it on the
   * store, and the table column read a day early only west of it.
   */
  private static final String[] ZONES = {
      "Pacific/Midway",       // -11
      "US/Mountain",          // -7/-6, the zone this budget is kept in
      "US/Eastern",
      "UTC",
      "Europe/Berlin",
      "Asia/Kathmandu",       // +5:45, not a whole hour
      "Asia/Tokyo",
      "Pacific/Kiritimati",   // +14
  };

  /** Ordinary days, month and year ends, and days on which a zone shifts. */
  private static final int[][] DAYS = {
      {2026, 1, 1}, {2026, 3, 8}, {2026, 4, 26}, {2026, 6, 30},
      {2026, 8, 31}, {2026, 9, 6}, {2026, 10, 25}, {2026, 11, 1}, {2026, 12, 31},
      {2024, 2, 29},            // a leap day
  };

  private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

  private TimeZone realDefault;

  @Override protected void setUp() throws Exception {
    super.setUp();
    realDefault = TimeZone.getDefault();
  }

  @Override protected void tearDown() throws Exception {
    // the zone is process-wide state; putting it back matters to every other test
    TimeZone.setDefault(realDefault);
    super.tearDown();
  }

  // ---------------------------------------------------------------- the wire

  /**
   * The wire form is midnight UTC, whoever is asking. This is the whole premise:
   * it is the one representation the zone-blind server and a client in any zone
   * can both read as the same day.
   */
  public void testWireFormIsAlwaysMidnightUtc() {
    for (String z : ZONES) {
      TimeZone.setDefault(TimeZone.getTimeZone(z));
      for (int[] d : DAYS) {
        java.sql.Date wire = Day.of(d[0], d[1], d[2]);
        assertEquals(describe(z, d) + " is not on a midnight boundary",
                     0L, wire.getTime() % 86400000L);
        Calendar c = new GregorianCalendar(UTC);
        c.setTime(wire);
        assertEquals(describe(z, d) + " year", d[0], c.get(Calendar.YEAR));
        assertEquals(describe(z, d) + " month", d[1], c.get(Calendar.MONTH) + 1);
        assertEquals(describe(z, d) + " day", d[2], c.get(Calendar.DAY_OF_MONTH));
      }
    }
  }

  /** The same calendar day built from two different zones is the same instant. */
  public void testTheWireFormDoesNotDependOnTheBuildersZone() {
    java.sql.Date first = null;
    for (String z : ZONES) {
      TimeZone.setDefault(TimeZone.getTimeZone(z));
      java.sql.Date wire = Day.of(2026, 8, 31);
      if (first == null) first = wire;
      else assertSameDay("Aug 31 differs when built in " + z, first, wire);
    }
  }

  /**
   * Out to the user's view and back again is the round trip the client makes on
   * every load and save, and it must not move the day. Before {@code Day} the
   * client built the instant at midnight in the <em>user's</em> zone, which lost
   * a day per round trip -- and the form's save-as-you-edit then wrote the lost
   * day back, so the error compounded.
   */
  public void testTheUsersViewRoundTripsWithoutMovingTheDay() {
    for (String z : ZONES) {
      TimeZone zone = TimeZone.getTimeZone(z);
      TimeZone.setDefault(zone);
      for (int[] d : DAYS) {
        java.sql.Date wire = Day.of(d[0], d[1], d[2]);
        java.util.Date view = Day.toView(wire, zone);

        // the view lands on the intended day, in the user's own zone
        Calendar c = new GregorianCalendar(zone);
        c.setTime(view);
        assertEquals(describe(z, d) + " shows the wrong year", d[0], c.get(Calendar.YEAR));
        assertEquals(describe(z, d) + " shows the wrong month", d[1], c.get(Calendar.MONTH) + 1);
        assertEquals(describe(z, d) + " shows the wrong day", d[2], c.get(Calendar.DAY_OF_MONTH));

        assertSameDay(describe(z, d) + " did not survive the round trip",
                      wire, Day.fromView(view, zone));
      }
    }
  }

  /**
   * Zones that shift at midnight are the nastiest case: local midnight does not
   * exist at all on that date, so the calendar silently hands back the shifted
   * instant.
   */
  public void testDaysSurviveZonesThatShiftAtMidnight() {
    for (String z : new String[]{"America/Santiago", "Asia/Beirut", "America/Havana"}) {
      TimeZone zone = TimeZone.getTimeZone(z);
      TimeZone.setDefault(zone);
      for (int[] d : DAYS) {
        java.sql.Date wire = Day.of(d[0], d[1], d[2]);
        assertSameDay(describe(z, d) + " did not survive a midnight shift",
                      wire, Day.fromView(Day.toView(wire, zone), zone));
      }
    }
  }

  /** Today is today where the user is, not where the machine's clock reads UTC. */
  public void testTodayIsTodayInTheUsersZone() {
    for (String z : ZONES) {
      TimeZone zone = TimeZone.getTimeZone(z);
      TimeZone.setDefault(zone);
      Calendar here = new GregorianCalendar(zone);
      java.sql.Date today = Day.today(zone);
      Calendar got = new GregorianCalendar(UTC);
      got.setTime(today);
      assertEquals(z + ": today is the wrong year", here.get(Calendar.YEAR), got.get(Calendar.YEAR));
      assertEquals(z + ": today is the wrong month", here.get(Calendar.MONTH), got.get(Calendar.MONTH));
      assertEquals(z + ": today is the wrong day",
                   here.get(Calendar.DAY_OF_MONTH), got.get(Calendar.DAY_OF_MONTH));
    }
  }

  /** A null day is a null day in both directions rather than an exception. */
  public void testNullDaysPassStraightThrough() {
    TimeZone.setDefault(TimeZone.getTimeZone("US/Mountain"));
    assertNull(Day.toView(null, TimeZone.getDefault()));
    assertNull(Day.fromView(null, TimeZone.getDefault()));
  }

  // ------------------------------------------------- java.sql.Date behaviour

  /**
   * {@code java.sql.Date.toString()} renders in the default zone, so it reports
   * the day before for a stored day anywhere west of Greenwich. It is the most
   * misleading thing in this whole area -- a debugger or a log line shows the
   * wrong day for a value that is perfectly correct -- so it is pinned here
   * rather than trusted anywhere.
   */
  public void testSqlDateToStringIsNotTheStoredDay() {
    java.sql.Date wire = Day.of(2026, 8, 31);

    TimeZone.setDefault(UTC);
    assertEquals("2026-08-31", wire.toString());

    TimeZone.setDefault(TimeZone.getTimeZone("US/Mountain"));
    assertEquals("toString() has stopped being zone-dependent; if that is deliberate,"
                 + " the warnings about it elsewhere can go",
                 "2026-08-30", wire.toString());
  }

  /**
   * Day.toIso spells the stored day whatever the default zone, which is what
   * makes it safe to log or compare where toString() is not.  The readiness
   * probe leans on this: it checks the day Hibernate read back against the day
   * the database says it holds, and a spelling that moved with the zone would
   * fail that check on every server west of Greenwich.
   */
  public void testTheIsoSpellingIsTheStoredDayInEveryZone() {
    for (String z : ZONES) {
      TimeZone.setDefault(TimeZone.getTimeZone(z));
      for (int[] d : DAYS) {
        assertEquals(describe(z, d) + " spelled wrong",
            String.format("%04d-%02d-%02d", d[0], d[1], d[2]), Day.toIso(Day.of(d[0], d[1], d[2])));
      }
    }
    assertNull("a null day has no spelling", Day.toIso(null));
  }

  /** Equality is by instant, so two builds of one day match however they were made. */
  public void testStoredDaysCompareByCalendarDay() {
    TimeZone.setDefault(TimeZone.getTimeZone("US/Mountain"));
    java.sql.Date aug31 = Day.of(2026, 8, 31);
    java.sql.Date sep1 = Day.of(2026, 9, 1);

    assertSameDay("the same day built twice differs", aug31, Day.of(2026, 8, 31));
    assertFalse("two different days compared equal", aug31.equals(sep1));
    assertTrue("Aug 31 did not sort before Sep 1", aug31.compareTo(sep1) < 0);
    assertEquals("a day is 24h from the next", 86400000L, sep1.getTime() - aug31.getTime());
  }

  /** The day has to survive Java serialization, because that is how it travels. */
  public void testStoredDaysSurviveSerialization() throws Exception {
    for (int[] d : DAYS) {
      java.sql.Date wire = Day.of(d[0], d[1], d[2]);
      assertSameDay("a stored day changed in serialization", wire, (java.sql.Date)roundTrip(wire));
    }
  }

  // ------------------------------------------------------- criteria evaluation

  /**
   * A {@code DetachedCriteria} is built on the client and Java-serialized to the
   * server, which is what pins this application to Hibernate 5.6. Its date
   * restrictions have to arrive intact.
   */
  public void testCriteriaCarryTheirDatesAcrossTheWire() throws Exception {
    TimeZone.setDefault(TimeZone.getTimeZone("US/Mountain"));
    java.util.Date begin = Day.of(2026, 8, 1);
    java.util.Date end = Day.of(2026, 8, 31);

    Hierarchy.CategoryTotal category =
        new Hierarchy.CategoryTotal("Groceries", 7, new Money("0.00"));
    Hierarchy.AccountTotal account =
        new Hierarchy.AccountTotal(new Account(), "Checking", 3, new Money("0.00"));

    for (Object thing : new Object[]{category, account}) {
      DetachedCriteria sent = CriteriaFactory.getInstance().getTransactions(thing, begin, end);
      assertEquals("the criteria changed crossing the wire for " + thing.getClass().getSimpleName(),
                   sent.toString(), roundTrip(sent).toString());

      DetachedCriteria balance =
          CriteriaFactory.getInstance().getBeginningBalance(thing, begin, Boolean.TRUE);
      assertEquals("the beginning-balance criteria changed crossing the wire",
                   balance.toString(), roundTrip(balance).toString());
    }
  }

  /**
   * What the server actually compares.
   *
   * <p>It binds the bound as a {@code java.sql.Date}, which becomes a SQL DATE in
   * the server's own zone -- UTC, deliberately, and the image must keep it that
   * way -- so the comparison is between the UTC day of the stored instant and the
   * UTC day of the bound. The query bar's choosers hand back local midnight, and
   * the point of this test is that the day the user picked is never dropped by
   * that mismatch.
   *
   * <p>East of Greenwich the bound lands on the previous UTC day, so the filter is
   * over-inclusive by one day there. That is recorded rather than asserted as
   * desirable: the bounds are deliberately instants -- a range filter on when,
   * not a day with a stored truth -- so widening is the safe direction.
   */
  public void testTheBeginDayThePickerGivesIsNeverExcluded() {
    for (String z : ZONES) {
      TimeZone zone = TimeZone.getTimeZone(z);
      TimeZone.setDefault(zone);
      for (int[] d : DAYS) {
        java.sql.Date stored = Day.of(d[0], d[1], d[2]);

        // what a JDateChooser hands back for that same day in this zone
        Calendar c = new GregorianCalendar(zone);
        c.clear();
        c.set(d[0], d[1] - 1, d[2], 0, 0, 0);
        java.util.Date bound = c.getTime();

        assertTrue(describe(z, d) + " would be filtered out by its own begin bound",
                   utcDay(stored).compareTo(utcDay(bound)) >= 0);
      }
    }
  }

  // ------------------------------------------------------------ table display

  /**
   * The transaction table had no renderer of its own, so it fell through to
   * Swing's, which formats in the default zone and therefore showed the day
   * before anywhere west of Greenwich -- disagreeing with the form's own date
   * field about the transaction being edited.
   */
  public void testTheTableShowsTheStoredDayInEveryZone() {
    for (String z : ZONES) {
      TimeZone.setDefault(TimeZone.getTimeZone(z));
      DayRenderer renderer = new DayRenderer();
      JTable table = new JTable();
      for (int[] d : DAYS) {
        String shown = render(renderer, table, Day.of(d[0], d[1], d[2]));
        assertEquals(describe(z, d) + " is not the day shown in the table",
                     expectedText(d), shown);
      }
    }
  }

  /**
   * {@code getColumnClass} answers with the runtime class of the value, which is
   * {@code java.sql.Date}; the renderer is registered against
   * {@code java.util.Date} and has to be found from it by superclass lookup. If
   * that ever stops holding, Swing's own renderer takes the column back and the
   * day silently shifts again.
   */
  public void testTheTableResolvesTheDayRendererForSqlDates() {
    TimeZone.setDefault(TimeZone.getTimeZone("US/Mountain"));
    JTable table = new JTable();
    assertFalse("Swing's own date renderer is already correct; DayRenderer may be moot",
                "Aug 31, 2026".equals(
                    render(table.getDefaultRenderer(java.sql.Date.class), table, Day.of(2026, 8, 31))));

    table.setDefaultRenderer(java.util.Date.class, new DayRenderer());
    TableCellRenderer resolved = table.getDefaultRenderer(java.sql.Date.class);
    assertTrue("a java.sql.Date no longer resolves to DayRenderer, so the column"
               + " has fallen back to Swing's zone-dependent one",
               resolved instanceof DayRenderer);
    assertEquals("Aug 31, 2026", render(resolved, table, Day.of(2026, 8, 31)));
  }

  /** A renderer is called once per cell per repaint, so it must not allocate. */
  public void testTheDayRendererReusesItsComponent() {
    TimeZone.setDefault(TimeZone.getTimeZone("US/Mountain"));
    DayRenderer renderer = new DayRenderer();
    JTable table = new JTable();
    assertSame("DayRenderer allocated a second component",
               renderer.getTableCellRendererComponent(table, Day.of(2026, 8, 31), false, false, 0, 1),
               renderer.getTableCellRendererComponent(table, Day.of(2026, 9, 1), false, false, 1, 1));
  }

  /** An empty cell and a non-date both have to render rather than throw. */
  public void testTheDayRendererCopesWithNullsAndNonDates() {
    TimeZone.setDefault(TimeZone.getTimeZone("US/Mountain"));
    DayRenderer renderer = new DayRenderer();
    JTable table = new JTable();
    assertEquals("", render(renderer, table, null));
    assertEquals("not a date", render(renderer, table, "not a date"));
  }

  // ------------------------------------------------------- the date pickers

  /**
   * The stock editor builds its {@code SimpleDateFormat} once, in whatever the
   * default zone was at construction, and never looks again. The zone here is a
   * live preference, so an editor built before the user changed it goes on
   * rendering in the old one -- which is how the text field came to show the 30th
   * while the popup calendar said the 31st.
   *
   * <p>This pins the stock behaviour deliberately. If a future jcalendar fixes
   * it, this test fails and {@link ZonedDateEditor} can go.
   */
  public void testTheStockEditorGoesStaleWhenTheZoneChanges() {
    TimeZone berlin = TimeZone.getTimeZone("Europe/Berlin");
    TimeZone.setDefault(berlin);
    JTextFieldDateEditor stock = new JTextFieldDateEditor("MM/dd/yyyy", "##/##/####", '_');

    java.util.Date view = Day.toView(Day.of(2026, 8, 31), berlin);
    stock.setDate(view);
    assertEquals("08/31/2026", stock.getText());

    TimeZone.setDefault(TimeZone.getTimeZone("US/Mountain"));
    stock.setDate(view);
    assertEquals("the stock editor has started following the default zone;"
                 + " ZonedDateEditor may no longer be needed",
                 "08/31/2026", stock.getText());
  }

  /** Ours re-reads the default zone on every render, so the same instant follows it. */
  public void testTheZonedEditorFollowsTheZone() {
    TimeZone berlin = TimeZone.getTimeZone("Europe/Berlin");
    TimeZone.setDefault(berlin);
    ZonedDateEditor zoned = new ZonedDateEditor("MM/dd/yyyy", "##/##/####", '_');

    java.util.Date view = Day.toView(Day.of(2026, 8, 31), berlin);
    zoned.setDate(view);
    assertEquals("08/31/2026", zoned.getText());

    // Berlin's midnight on Aug 31 is 16:00 on Aug 30 in Mountain.  The instant has
    // not moved, so a zone-aware editor must now say so.
    TimeZone.setDefault(TimeZone.getTimeZone("US/Mountain"));
    zoned.setDate(view);
    assertEquals("ZonedDateEditor did not follow the zone change", "08/30/2026", zoned.getText());
  }

  /**
   * The picker round trip the form makes: a stored day in, the user's day out.
   * This is {@code TransactionChangeHandler.showDate} on the way in and
   * {@code ChangeableDateChooser.getValue} on the way out.
   */
  public void testThePickerRoundTripsAStoredDay() {
    for (String z : ZONES) {
      TimeZone zone = TimeZone.getTimeZone(z);
      TimeZone.setDefault(zone);
      JDateChooser chooser = new JDateChooser(new ZonedDateEditor("MM/dd/yyyy", "##/##/####", '_'));
      for (int[] d : DAYS) {
        java.sql.Date stored = Day.of(d[0], d[1], d[2]);
        chooser.setDate(Day.toView(stored, zone));                 // showDate
        assertSameDay(describe(z, d) + " did not survive the picker",
                      stored, Day.fromView(chooser.getDate(), zone));  // getValue
      }
    }
  }

  /**
   * The mistake this whole area keeps making: re-deriving a display value from
   * the previous display value. A picker's {@code getDate()} is an instant, so
   * re-setting it under a new zone reinterprets it as a different day and walks
   * the shown day one further off per change -- five switches, five days. Putting
   * the <em>stored</em> day back through {@code Day.toView} is stable no matter
   * how many times the zone moves.
   */
  public void testAPickerMustBeResetFromTheStoredDayNotFromItself() {
    TimeZone berlin = TimeZone.getTimeZone("Europe/Berlin");
    TimeZone.setDefault(berlin);

    java.sql.Date stored = Day.of(2026, 8, 31);
    JDateChooser wrong = new JDateChooser(new ZonedDateEditor("MM/dd/yyyy", "##/##/####", '_'));
    JDateChooser right = new JDateChooser(new ZonedDateEditor("MM/dd/yyyy", "##/##/####", '_'));
    wrong.setDate(Day.toView(stored, berlin));
    right.setDate(Day.toView(stored, berlin));

    String[] switches = {"US/Mountain", "Asia/Tokyo", "Pacific/Midway", "Europe/Berlin", "US/Eastern"};
    for (String z : switches) {
      TimeZone zone = TimeZone.getTimeZone(z);
      TimeZone.setDefault(zone);
      wrong.setDate(wrong.getDate());                  // the mistake
      right.setDate(Day.toView(stored, zone));         // what showDate does

      assertSameDay("resetting from the stored day moved it in " + z,
                    stored, Day.fromView(right.getDate(), zone));
    }

    TimeZone lastZone = TimeZone.getTimeZone(switches[switches.length - 1]);
    assertFalse("resetting a picker from its own getDate() has stopped walking the day;"
                + " if jcalendar or Day changed, redrawDatePickers' warning can be relaxed",
                stored.equals(Day.fromView(wrong.getDate(), lastZone)));
  }

  // ------------------------------------------------------------------ helpers

  /**
   * Compare two stored days and say something true when they differ.
   *
   * <p>{@code assertEquals} on a {@code java.sql.Date} reports through
   * {@code toString()}, which renders in the default zone -- so two instants a
   * day apart can print identically and the failure reads
   * "expected:&lt;2026-08-30&gt; but was:&lt;2026-08-30&gt;". In a suite about exactly
   * that trap, the message has to be zone-proof.
   */
  private static void assertSameDay(String message, java.sql.Date expected, java.sql.Date actual) {
    if (expected == null ? actual == null : expected.equals(actual)) return;
    fail(message + " -- expected " + describeInstant(expected) + " but was " + describeInstant(actual));
  }

  private static String describeInstant(java.sql.Date d) {
    return d == null ? "null" : utcDay(d) + " UTC (" + d.getTime() + ")";
  }

  /** The day an instant falls on in UTC, which is what the server compares. */
  private static String utcDay(java.util.Date instant) {
    Calendar c = new GregorianCalendar(UTC);
    c.setTime(instant);
    return String.format("%04d-%02d-%02d",
                         c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
  }

  /** What the table's MEDIUM date format makes of a day, independent of zone. */
  private static String expectedText(int[] d) {
    java.text.DateFormat f = java.text.DateFormat.getDateInstance();
    f.setTimeZone(UTC);
    Calendar c = new GregorianCalendar(UTC);
    c.clear();
    c.set(d[0], d[1] - 1, d[2], 0, 0, 0);
    return f.format(c.getTime());
  }

  private static String render(TableCellRenderer r, JTable t, Object value) {
    return ((JLabel)r.getTableCellRendererComponent(t, value, false, false, 0, 1)).getText();
  }

  private static Object roundTrip(Object o) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream out = new ObjectOutputStream(bytes);
    out.writeObject(o);
    out.close();
    return new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();
  }

  private static String describe(String zone, int[] d) {
    return String.format("%04d-%02d-%02d in %s", d[0], d[1], d[2], zone);
  }
}
