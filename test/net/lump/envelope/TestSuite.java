package us.lump.envelope;

import junit.framework.Test;
import junit.framework.TestCase;
import us.lump.envelope.client.TestSecurity;
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
 * @version $Id: TestSuite.java,v 1.2 2007/08/18 04:49:44 troy Exp $
 */
public class TestSuite extends TestCase {
  public static final int SERVER_RMI_PORT = 7041;
  public static final int SERVER_HTTP_PORT = 7042;
  public static final String SERVER_PROPERTY = "server";
  public static String SERVER_HOST_NAME = "localhost";

  static {
    Properties p = System.getProperties();

    try {
      if (p.containsKey(SERVER_PROPERTY))
        SERVER_HOST_NAME = p.getProperty(SERVER_PROPERTY);
      else SERVER_HOST_NAME = localHost();
      URL url = new URL("http://" + SERVER_HOST_NAME + ":" + SERVER_HTTP_PORT + "/");
      p.put("java.rmi.server.codebase", url);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }

    p.put("java.rmi.server.rminode", "rmi://" + SERVER_HOST_NAME + ":" + Integer.toString(SERVER_RMI_PORT) + "/");
  }

  /**
   * Gets the Controller for testing.
   *
   * @return Controller.
   * @throws MalformedURLException
   * @throws NotBoundException
   * @throws RemoteException
   */
  public static Controller getController() throws MalformedURLException, NotBoundException, RemoteException {
    String rmiName = (String) System.getProperties().get("java.rmi.server.rminode");
    return (Controller) Naming.lookup(rmiName + "Controller");
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
   * @throws UnknownHostException
   */
  private static String localHost() throws UnknownHostException {
    final java.net.InetAddress localMachine = java.net.InetAddress.getLocalHost();
    return localMachine.getHostName();
  }

}

