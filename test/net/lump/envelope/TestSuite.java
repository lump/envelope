package us.lump.envelope;

import junit.framework.Test;
import junit.framework.TestCase;
import us.lump.envelope.client.TestSecurity;
import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.server.PrefsConfigurator;
import us.lump.envelope.server.rmi.Controller;
import us.lump.lib.TestMoney;
import us.lump.lib.util.TestEncryption;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Properties;

/**
 * A JUnit class which runs all tests.
 *
 * @author Troy Bowman
 * @version $Id: TestSuite.java,v 1.5 2007/08/21 05:01:20 troy Exp $
 */
public class TestSuite extends TestCase {
  public static int SERVER_RMI_PORT = 7041;
  public static int SERVER_HTTP_PORT = 7042;
  public static final String HOST_NAME_PROPERTY = "server";
  public static final String RMI_PORT_PROPERTY = "server.rmi.port";
  public static final String HTTP_PORT_PROPERTY = "server.http.port";
  public static String SERVER_HOST_NAME = "localhost";

  public static final String USER = "guest";
  public static final LoginSettings loginSettings = LoginSettings.getInstance();

  static {
    Properties system = System.getProperties();

    try {
      if (system.containsKey(HOST_NAME_PROPERTY)) {
        SERVER_HOST_NAME = system.getProperty(HOST_NAME_PROPERTY);
        if (system.containsKey(RMI_PORT_PROPERTY))
          SERVER_RMI_PORT =
              Integer.parseInt(system.getProperty(RMI_PORT_PROPERTY));
        if (system.containsKey(HTTP_PORT_PROPERTY))
          SERVER_HTTP_PORT =
              Integer.parseInt(system.getProperty(HTTP_PORT_PROPERTY));
      } else {
        SERVER_HOST_NAME = localHost();

        Properties serverConfig = PrefsConfigurator.configure(Server.class);
        SERVER_RMI_PORT = Integer.parseInt(
            serverConfig.getProperty("server.rmi.port"));
        SERVER_HTTP_PORT = Integer.parseInt(
            serverConfig.getProperty("server.http.classloader.port"));
      }

      URL url =
          new URL("http://" + SERVER_HOST_NAME + ":" + SERVER_HTTP_PORT + "/");
      system.put("java.rmi.server.codebase", url);

      loginSettings.setUsername(USER);
      loginSettings.setPassword(USER);

    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }

    system.put("java.rmi.server.rminode",
               "rmi://"
               + SERVER_HOST_NAME
               + ":"
               + Integer.toString(SERVER_RMI_PORT)
               + "/");
  }

  /**
   * Gets the Controller for testing.
   *
   * @return Controller.
   *
   * @throws MalformedURLException
   * @throws NotBoundException
   * @throws RemoteException
   */
  public static Controller getController()
      throws MalformedURLException, NotBoundException, RemoteException {
    String rmiName =
        (String)System.getProperties().get("java.rmi.server.rminode");
    return (Controller)Naming.lookup(rmiName + "Controller");
  }

  /**
   * The Test suite itself.
   *
   * @return Test
   */
  public static Test suite() {
    junit.framework.TestSuite suite = new junit.framework.TestSuite();
    suite.addTestSuite(TestMoney.class);
    suite.addTestSuite(TestEncryption.class);
    suite.addTestSuite(TestSecurity.class);
    return suite;
  }

  /**
   * Figure out local hosts's name.
   *
   * @return String
   *
   * @throws UnknownHostException
   */
  private static String localHost() throws UnknownHostException {
    final java.net.InetAddress localMachine =
        java.net.InetAddress.getLocalHost();
    return localMachine.getHostName();
  }

}

