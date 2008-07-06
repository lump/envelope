import org.apache.log4j.BasicConfigurator;
import us.lump.envelope.client.ui.Preferences;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.client.ui.prefs.ServerSettings;
import us.lump.lib.util.EmacsKeyBindings;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RMISecurityManager;
import java.rmi.server.RMIClassLoader;

/**
 * The class that starts the client by bootstrapping from RMI.
 *
 * @author Troy Bowman
 * @version $Id: Envelope.java,v 1.4 2008/07/06 04:14:24 troy Exp $
 */

public class Envelope {

  private Envelope() throws
      MalformedURLException,
      ClassNotFoundException,
      InstantiationException,
      IllegalAccessException {

    EmacsKeyBindings.loadEmacsKeyBindings();

    Preferences prefs = Preferences.getInstance();
    prefs.setTitle(Strings.get("preferences"));
    if (!prefs.areServerSettingsOk()) {
      prefs.selectTab(Strings.get("server"));
      prefs.setVisible(true);
    }
    ServerSettings ss = ServerSettings.getInstance();

    try {
      URL url = ss.getCodeBase();
      String className = "us.lump.envelope.Client";
      Class clientClass = RMIClassLoader.loadClass(url, className);
      ((Runnable)clientClass.newInstance()).run();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * main method.
   *
   * @param args command line args
   */
  public static void main(String args[]) {
    URL securityPolicy = Envelope.class.getResource("security.policy");
    if (securityPolicy != null) {
      System.setProperty("java.security.policy", securityPolicy.toString());
    }
    if (System.getSecurityManager() == null) {
      System.setSecurityManager(new RMISecurityManager());
    }

    BasicConfigurator.configure();

    try {
      new Envelope();
    } catch (MalformedURLException mURLe) {
      System.err
          .println("URL not specified correctly for the Client class: "
                   + mURLe);
      System.exit(1);
    } catch (ClassNotFoundException cnfe) {
      System.err.println("Envelope, Class not found: " + cnfe);
      System.exit(1);
    } catch (InstantiationException ie) {
      System.err.println("Envelope, class could not be instantiated" + ie);
      System.exit(1);
    } catch (IllegalAccessException iae) {
      System.err.println("Internal error" + iae);
      System.exit(1);
    }
  }
}
