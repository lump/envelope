package us.lump.envelope;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import us.lump.envelope.server.PrefsConfigurator;
import us.lump.envelope.server.dao.DAO;
import us.lump.envelope.server.http.ClassServer;
import us.lump.envelope.server.log.Logging;
import us.lump.envelope.server.rmi.Controlled;
import us.lump.envelope.server.rmi.Controller;
import us.lump.lib.util.Span;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.Scanner;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Server Main Code.
 *
 * @author Troy Bowman
 * @version $Id: Server.java,v 1.6 2007/08/21 04:09:10 troy Exp $
 */

public class Server {

  private static Properties serverConfig = null;
  private static String LOCAL_HOST = "localhost";
  private static final long START_TIME = System.currentTimeMillis();

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

  // Constructor
  private Server()
      throws RemoteException,
      MalformedURLException,
      NotBoundException {

    System.getProperties().put(
        "java.rmi.server.codebase",
        "http://" + LOCAL_HOST + ":"
        + serverConfig.getProperty("server.rmi.port") + "/");

    LocateRegistry.createRegistry(
        Integer.parseInt(serverConfig.getProperty("server.rmi.port")));

    logger.info(MessageFormat.format(
        "Registry created on host computer {0} on port {1}",
        LOCAL_HOST,
        serverConfig.getProperty("server.rmi.port"))
    );

    Controller controller = new Controlled();
    logger.info("Remote Controller implementation object created");

    Naming.rebind("//"
                  + LOCAL_HOST
                  + ":"
                  + serverConfig.getProperty("server.rmi.port")
                  + "/Controller", controller);

    logger.info("Bindings Finished, waiting for client requests.");
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
      if ("-h".equals(arg) || "--help".equals(arg)) {
        System.out.println("-h --help: this help.");
        System.out
            .println("-q --quiet: not interactive, no stdout/stderr logging.");
        System.out.println("--conf: reconfigure everything.");
        System.out.println("--reset: reset everything to defaults.");
        for (Class c : confClasses) {
          System.out.format("--%s-conf: reconfigure %s.%s",
                            c.getSimpleName().toLowerCase(),
                            c.getSimpleName(),
                            System.getProperty("line.separator"));
          System.out
              .format("--%s-reset: reset %s to defaults, forcing reconfigure%s",
                      c.getSimpleName().toLowerCase(),
                      c.getSimpleName(),
                      System.getProperty("line.separator"));
        }
        System.exit(0);
      }
      if (arg.matches("^-q|--quiet$")) fg = false;
      for (Class c : confClasses) {
        if (arg.matches("^--(?:"
                        + c.getSimpleName().toLowerCase()
                        + "-)?conf$"))
          Preferences.userNodeForPackage(c)
              .put(c.getSimpleName() + ".ok", "no");
        if (arg.matches("^--(?:"
                        + c.getSimpleName().toLowerCase()
                        + "-)?reset$"))
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
        Preferences.userNodeForPackage(Logging.class).put("log4j.rootLogger",
                                                          rootlogger
                                                          + ", console");
      else if (!fg && rootlogger.matches("^.*?console.*$"))
        Preferences.userNodeForPackage(Logging.class).put("log4j.rootLogger",
                                                          rootlogger.replaceAll(
                                                              "^(.*?),?\\s+console(.*)$",
                                                              "$1$2"));
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

    // start the HTTP class server
    try {
      new ClassServer(Integer.parseInt(serverConfig.getProperty(
          "server.http.classloader.port")));
      logger.info(MessageFormat.format("ClassServer started on port {0}",
                                       serverConfig.getProperty(
                                           "server.http.classloader.port")));
    }
    catch (IOException e) {
      logger.error("Unable to start ClassServer: " + e.getMessage());
      e.printStackTrace();
    }

    // start the RMI server
    try {
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
          if (line.matches("^q(?:uit)?$")) {
            System.err.println("Exiting...");
            System.exit(0);
          } else {
            // default will print uptime
            System.out
                .println(MessageFormat.format(
                    "uptime: {0} since: {1,date,full} {1,time,full}",
                    Span.interval(START_TIME, System.currentTimeMillis()),
                    START_TIME));
          }
        }
      }
    }
    catch (java.rmi.UnknownHostException uhe) {
      logger.error(
          MessageFormat.format(
              "The host computer name you have specified, {0} does not match your real computer name.",
              LOCAL_HOST)
      );
    }
    catch (RemoteException re) {
      logger.error("Error starting service");
      System.out.println("" + re);
    }
    catch (MalformedURLException mURLe) {
      logger.error("Internal error" + mURLe);
    }
    catch (NotBoundException nbe) {
      logger.error("Not Bound");
      System.out.println("" + nbe);
    }
  }


}
