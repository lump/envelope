package net.lump.envelope.client.ui.components.forms.transaction;

import net.lump.lib.util.Day;

import javax.swing.table.DefaultTableCellRenderer;
import java.text.DateFormat;
import java.util.TimeZone;

/**
 * Renders a stored calendar day as that day, in every zone.
 *
 * <p>{@code Transaction.date} is a calendar day -- "August 31st", the same day
 * for everyone -- carried on the wire as midnight UTC (see {@link Day}). The
 * transaction table had no renderer registered for it, so it fell through to
 * Swing's own {@code DefaultTableCellRenderer.DateRenderer}, which formats with
 * {@code DateFormat.getDateInstance()} in the <em>default</em> zone. Midnight UTC
 * is the evening before anywhere west of Greenwich, so every date in the table
 * read a day early for a user in the Americas while the form's own date field --
 * which goes through {@code Day.toView} -- read correctly. Two different days for
 * one transaction, on screen at once.
 *
 * <p>The fix is not to push the model's value through {@code Day.toView} as the
 * field does. The field has to, because a {@code JDateChooser} thinks in
 * instants and has to be handed one that lands on the right day locally. A table
 * cell is just text, so it can say what the stored day actually is: formatting
 * the stored instant in UTC yields the day the column means. That also makes this
 * immune to the zone preference changing under it -- a calendar day does not move
 * when the reader does, and re-deriving a display value from a previous display
 * value is what walked the date pickers a day further off on every switch.
 *
 * @author Troy Bowman
 */
public class DayRenderer extends DefaultTableCellRenderer {

  /**
   * Fixed to UTC, because that is the zone the stored instant's day is expressed
   * in, not because the reader is there. Not thread safe, which is fine: cell
   * rendering happens only on the EDT.
   */
  private final DateFormat dayFormat;

  public DayRenderer() {
    super();
    dayFormat = DateFormat.getDateInstance();
    dayFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  /**
   * {@inheritDoc}
   *
   * <p>Overriding this rather than {@code getTableCellRendererComponent} keeps the
   * single inherited label, so nothing is allocated per cell per repaint.
   */
  @Override protected void setValue(Object value) {
    setText(value instanceof java.util.Date
            ? dayFormat.format((java.util.Date)value)
            : (value == null ? "" : value.toString()));
  }
}
