package net.lump.envelope.client.ui.components;

import com.toedter.calendar.JTextFieldDateEditor;

import java.util.Date;
import java.util.TimeZone;

/**
 * A JTextFieldDateEditor whose text follows the user's time zone.
 *
 * <p>The stock editor makes its SimpleDateFormat once, at construction, in
 * whatever the JVM's default zone was at that moment -- and never looks again.
 * The user's zone is a preference they can change while the client is running,
 * applied as the JVM default; an editor built before the change went on
 * rendering and parsing days in the old zone, so the text field could show the
 * 30th while the popup calendar said the 31st.  Re-applying the format string
 * does not help: the rebuilt formatter still gets a captured zone.
 *
 * <p>dateFormatter is protected, so this editor simply puts it on the current
 * default zone before every render and every parse.  Cheap, and always right.
 */
public class ZonedDateEditor extends JTextFieldDateEditor {

  public ZonedDateEditor(String datePattern, String maskPattern, char placeholder) {
    super(datePattern, maskPattern, placeholder);
  }

  private void followDefaultZone() {
    if (dateFormatter != null) dateFormatter.setTimeZone(TimeZone.getDefault());
  }

  @Override
  public void setDate(Date date) {
    followDefaultZone();
    super.setDate(date);
  }

  @Override
  protected void setDate(Date date, boolean firePropertyChange) {
    followDefaultZone();
    super.setDate(date, firePropertyChange);
  }

  @Override
  public Date getDate() {
    followDefaultZone();
    return super.getDate();
  }

  @Override
  public void setDateFormatString(String pattern) {
    super.setDateFormatString(pattern);
    followDefaultZone();
  }
}
