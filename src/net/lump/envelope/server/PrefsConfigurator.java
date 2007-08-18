package us.lump.envelope.server;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.Scanner;
import java.util.prefs.Preferences;

/**
 * Helps configure the server from defined defaults into java's Preferences for
 * the application it is applied.
 *
 * @author Troy Bowman
 * @version $Id: PrefsConfigurator.java,v 1.2 2007/08/18 23:20:11 troy Test $
 */
public class PrefsConfigurator {

  /**
   * Returns a properties object for the configuration of a class.
   *
   * @param cless the class being configured
   *
   * @return Properties
   */
  public static Properties configure(Class cless) {

    // yank the properties file from conventionized properties file
    Properties config = new Properties();
    try {
      config.load(
          cless.getResourceAsStream(cless.getSimpleName() + ".properties"));
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }

    // make a sorted array of names
    String[] customizableNames = config.keySet().toArray(new String[0]);
    Arrays.sort(customizableNames);

    // yank the preferences for class
    Preferences pref = Preferences.userNodeForPackage(cless);

    // sync and sanitize the prefs and properties
    for (String key : customizableNames) {
      // if the pref is null, set it from the config.
      if (null == pref.get(key, null)) pref.put(key, config.getProperty(key));
        // else, set the config from the pref.
      else config.put(key, pref.get(key, config.getProperty(key)));
    }

    while (!"ok".equals(pref.get(cless.getSimpleName() + ".ok", null))) {

      // print out the list and get approval
      String selector = null;
      System.out.println();
      for (int x = 0; x < customizableNames.length; x++) {
        System.out.format("%2d. %s=%s%s",
                          x + 1,
                          customizableNames[x],
                          config.getProperty(customizableNames[x]),
                          System.getProperty("line.separator"));
      }
      boolean invalid = true;
      while (invalid) {
        System.out.format(
            "Is this the configuration you prefer for %s? (y/n/1-%s/q) > ",
            cless.getSimpleName(),
            customizableNames.length);
        selector = new Scanner(System.in)
            .useDelimiter("\\r\\n|\\r|\\n")
            .nextLine();
        if (selector.matches("^[Yy](?:[Ee][Ss])?$")) {
          pref.put(cless.getSimpleName() + ".ok", "ok");
          invalid = false;
        } else if (selector.matches("^[Qq]$")) {
          System.out.println("Aborting...");
          System.exit(1);
        }
        if (!selector.matches("^\\s*$")) invalid = false;
      }

      // if it's not ok, lets step through all or the selection
      if (!"ok".equals(pref.get(cless.getSimpleName() + ".ok", null))) {
        int start = 0;
        int end = customizableNames.length;
        if (selector.matches("^\\d+$")) {
          int sel = Integer.parseInt(selector);
          if (sel > 0 && sel < customizableNames.length + 1) {
            start = sel - 1;
            end = start + 1;
          }
        }
        for (int x = start; x < end; x++) {
          String key = customizableNames[x];

          System.out.format("%s=[%s] > ", key, config.getProperty(key));
          String in = new Scanner(System.in).nextLine();
          if (in != null) {
            // if it's empty, accept the default
            if (in.matches("^\\s*$")) pref.put(key, config.getProperty(key));
              // if it's not empty, put the provided string
            else pref.put(key, in);
            // set the config property from the pref we just set.
            config.setProperty(key, pref.get(key, config.getProperty(key)));
          }
        }
      }
    }

    return config;
  }
}
