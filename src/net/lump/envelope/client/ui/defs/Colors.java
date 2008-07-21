package us.lump.envelope.client.ui.defs;

import java.awt.*;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

/**
 * Keeps our color definitions.
 *
 * @author troy
 * @version $Id: Colors.java,v 1.3 2008/07/21 21:59:18 troy Test $
 */
public class Colors {
  private HashMap<String, Color> colors = new HashMap<String, Color>();
  private static Colors instance;

  private Colors() {

    try {
      Properties colorProperties = new Properties();
      colorProperties.load(this.getClass().getResourceAsStream(
          this.getClass().getSimpleName() + ".properties"));

      Enumeration names = colorProperties.propertyNames();
      while (names.hasMoreElements()) {
        String name = (String)names.nextElement();
        String[] colordef = colorProperties.getProperty(name).split(",");
        if (colordef.length == 1 && colordef[0].matches("^\\d+$"))
          colors.put(name, new Color(Integer.parseInt(colordef[0])));
        if (colordef.length == 3 && colordef[0].matches("^\\d+$"))
          colors.put(name,
                     new Color(
                         Integer.parseInt(colordef[0]),
                         Integer.parseInt(colordef[1]),
                         Integer.parseInt(colordef[2])));
        if (colordef.length == 3 && colordef[0].matches("^\\d+\\.\\d+$"))
          colors.put(name, new Color(
              Float.parseFloat(colordef[0]),
              Float.parseFloat(colordef[1]),
              Float.parseFloat(colordef[2])));
      }
    } catch (IOException e1) {
      System.err.println("Couldn't load colors");
    }
  }

  public static Colors getInstance() {
    if (instance == null) instance = new Colors();
    return instance;
  }

  public static Color getColor(String color) {
    return getInstance().colors.get(color);
  }
}
