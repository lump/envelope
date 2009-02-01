package us.lump.envelope;

import junit.framework.Test;
import junit.framework.TestCase;
import org.apache.log4j.BasicConfigurator;
import us.lump.envelope.client.TestQuery;
import us.lump.envelope.client.TestSecurity;
import us.lump.envelope.client.portal.SecurityPortal;
import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.client.ui.prefs.ServerSettings;
import us.lump.envelope.exception.AbortException;
import us.lump.envelope.server.PrefsConfigurator;
import us.lump.envelope.server.rmi.Controller;
import us.lump.lib.TestMoney;
import us.lump.lib.util.TestEncryption;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

/**
 * A JUnit class which runs all tests.
 *
 * @author Troy Bowman
 * @version $Id: TestSuite.java,v 1.10 2009/02/01 02:33:42 troy Test $
 */
public class TestSuite extends TestCase {
  public static int DEFAULT_SERVER_PORT = 7041;
  public static final String HOST_PROPERTY = "server";
    public static final String PORT_PROPERTY = "server.port";
  public static String SERVER_HOST = "localhost" + DEFAULT_SERVER_PORT;

  public static final String USER = "bowmantest";
  public static final String PASSWORD = "guest";
  public static final LoginSettings LOGINSETTINGS = LoginSettings.getInstance();
  private static Boolean authed = null;

  static {
    BasicConfigurator.configure();

    Properties system = System.getProperties();

    try {
      ServerSettings ss = ServerSettings.getInstance();

      if (system.containsKey(HOST_PROPERTY)) {
        ss.setHostName(system.getProperty(HOST_PROPERTY));
        ss.setPort(system.containsKey(PORT_PROPERTY)
                        ? system.getProperty(PORT_PROPERTY)
                        : String.valueOf(DEFAULT_SERVER_PORT));
      } else {
        Properties serverConfig = PrefsConfigurator.configure(Server.class);
        ss.setHostName(localHost() +":" + DEFAULT_SERVER_PORT);
      }

      LOGINSETTINGS.setUsername(USER);
      LOGINSETTINGS.setPassword(PASSWORD);

      assertTrue("not authed", authed());

    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }

  }

  public static boolean authed() throws
      NoSuchAlgorithmException,
      BadPaddingException,
      IOException,
      IllegalBlockSizeException,
      InvalidKeyException,
      NoSuchPaddingException, AbortException {
    if (authed == null) {
      SecurityPortal sp = new SecurityPortal();
      authed = sp.auth(LOGINSETTINGS.challengeResponse(sp.getChallenge()));
    }
    return authed;
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
    suite.addTestSuite(TestQuery.class);
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

