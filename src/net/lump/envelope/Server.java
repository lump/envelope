package us.lump.envelope;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import us.lump.envelope.server.PrefsConfigurator;
import us.lump.envelope.server.dao.DAO;
import us.lump.envelope.server.http.SocketServer;
import us.lump.envelope.server.log.Logging;
import us.lump.lib.util.Interval;

import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.rmi.RMISecurityManager;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.Scanner;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Server Main Code.
 *
 * @author Troy Bowman
 * @version $Id: Server.java,v 1.14 2009/02/01 02:33:42 troy Test $
 */

public class Server {

  private static Properties serverConfig = null;
  private static String LOCAL_HOST = "localhost";
  private static final long START_TIME = System.currentTimeMillis();

  public static final String SERVER_PORT_PROPERTY = "server.port";

  static {
    final java.net.InetAddress localMachine;
    try {
      localMachine = java.net.InetAddress.getLocalHost();
      LOCAL_HOST = localMachine.getHostName();
    } catch (UnknownHostException e) {
      // nevermind
    }
  }

  private static final Logger logger = Logger.getLogger(Server.class);

  public static String getConfig(String property) {
    return serverConfig.getProperty(property);
  }

  // Constructor
  private Server() {

    // start the socket server
    try {
      new SocketServer(Integer.parseInt(
          serverConfig.getProperty(SERVER_PORT_PROPERTY))).startServer();
      logger.info(MessageFormat.format(
          "Server started on port {0}",
          serverConfig.getProperty(SERVER_PORT_PROPERTY)));
    }
    catch (IOException e) {
      logger.error("Unable to start SocketServer: " + e.getMessage());
      e.printStackTrace();
    }

  }

  /**
   * The main method.
   *
   * @param args command line arguments.
   */
  public static void main(String[] args) {

    Class[] confClasses = new Class[]{Server.class, DAO.class, Logging.class};
    boolean fg = true;
    for (String arg : args) {
      if (arg.matches("^-h|--help$")) {
        System.out.println("-h --help: this help.");
        System.out
            .println("-q --quiet: not interactive, no stdout/stderr logging.");
        System.out.println("--conf: reconfigure everything.");
        System.out.println("--reset: reset everything to defaults.");
        for (Class c : confClasses) {
          System.out.format("--conf-%s: reconfigure %s.%s",
                            c.getSimpleName().toLowerCase(),
                            c.getSimpleName(),
                            System.getProperty("line.separator"));
          System.out
              .format("--reset-%s: reset %s to defaults (forces review)%s",
                      c.getSimpleName().toLowerCase(),
                      c.getSimpleName(),
                      System.getProperty("line.separator"));
        }
        System.exit(0);
      }
      if (arg.matches("^-q|--quiet$")) fg = false;
      for (Class c : confClasses) {
        if (arg.matches("^--conf(?:-"
                        + c.getSimpleName().toLowerCase()
                        + ")?$"))
          Preferences.userNodeForPackage(c)
              .put(c.getSimpleName() + ".ok", "no");
        if (arg.matches("^--reset(?:-"
                        + c.getSimpleName().toLowerCase()
                        + ")?t$"))
          try {
            Preferences.userNodeForPackage(c).clear();
          } catch (BackingStoreException e) {
            e.printStackTrace();
            System.exit(1);
          }
      }
    }

    String rootlogger = Preferences.userNodeForPackage(Logging.class)
        .get("log4j.rootLogger", null);
    if (null != rootlogger) {
      if (fg && !rootlogger.matches("^.*?console.*$"))
        Preferences
            .userNodeForPackage(Logging.class)
            .put("log4j.rootLogger", rootlogger + ", console");
      else if (!fg && rootlogger.matches("^.*?console.*$"))
        Preferences
            .userNodeForPackage(Logging.class)
            .put("log4j.rootLogger",
                 rootlogger.replaceAll("^(.*?),?\\s+console(.*)$","$1$2"));
    }

    // initialize the server config
    serverConfig = PrefsConfigurator.configure(Server.class);

    // configure log4j
    PropertyConfigurator.configure(PrefsConfigurator.configure(Logging.class));

    // set up the security policy
    URL securityPolicy = Server.class.getResource("security.policy");
    if (securityPolicy != null) {
      System.setProperty("java.security.policy", securityPolicy.toString());
    }
    if (System.getSecurityManager() == null) {
      System.setSecurityManager(new RMISecurityManager());
    }

    // initialize hibernate
    DAO.initialize();

    // server
    new Server();

    // if we're in the foreground, listen for commands on the console
    if (fg) {
      Scanner s = new Scanner(System.in);
// Java 6 console
//        Console console = System.console();
//        if (console != null)
      while (true) {
        String line = s.nextLine();
//            String line = console.readLine();
        // quit command
        if (line == null || line.matches("^(?:q(?:uit)?|exit)$")) {
          System.err.println("Exiting...");
          System.exit(0);
        } else {
          // default will print uptime
          System.out
              .println(MessageFormat.format(
                  "uptime: {0} since: {1,date,full} {1,time,full}",
                  uptime(), START_TIME));
        }
      }
    }
  }

  public static String uptime() {
    return Interval.span(START_TIME, System.currentTimeMillis());
  }
}
