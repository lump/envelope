package us.lump.envelope.client.ui.defs;

import java.awt.*;
import java.util.HashMap;
import java.util.Set;

/**
 * .
 *
 * @author troy
 * @version $Id: Fonts.java,v 1.1 2007/07/21 20:15:04 troy Exp $
 */
public class Fonts {

  private static HashMap<String, Font> fonts = new HashMap<String, Font>();

  static {
    fonts.put("serif-36-bold-italic", new Font("serif", Font.ITALIC + Font.BOLD, 36));

    Font sans14Bold = new Font("Lucida Grande", Font.BOLD, 14);
    if (sans14Bold == null) sans14Bold = new Font("SansSerif", Font.BOLD, 14);
    fonts.put("sans-14-bold", sans14Bold);

    Font sans10 = new Font("Lucida Grande", Font.PLAIN, 10);
    if (sans10 == null) sans10 = new Font("SansSerif", Font.PLAIN, 10);
    fonts.put("sans-10-bold", sans10);
  }

  public static Font getFont(String name) {
    return fonts.get(name);
  }

  public static Set<String> getNames() {
    return fonts.keySet();
  }
}