package us.lump.envelope.client.ui.defs;

import org.apache.log4j.Logger;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Strings interface, for i18n.
 *
 * @author Troy Bowman
 * @version $Id: Strings.java,v 1.2 2007/08/18 23:20:11 troy Test $
 */
public class Strings {
  private ResourceBundle stringProperties;
  private Logger logger;
  private static Strings strings;

  private Strings() {
    stringProperties =
        ResourceBundle.getBundle(this.getClass().getName(),
                                 Locale.getDefault());
    logger = Logger.getLogger(this.getClass());
  }

  public static Strings getInstance() {
    if (strings == null) strings = new Strings();
    return strings;
  }

  public static ResourceBundle getResourceBundle() {
    return getInstance().stringProperties;
  }

  public static String get(String key) {
    Strings s = getInstance();
    String value = key;

    try {
      value = s.stringProperties.getString(key);
//      s.logger.debug(key + "=\"" + value + "\"");
    }
    catch (MissingResourceException e) {
      s.logger.error("could not find string property \""
                     + key + "\" from resource bundle");
    }

    return value;
  }
}
