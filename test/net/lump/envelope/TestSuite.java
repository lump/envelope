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
import us.lump.lib.TestMoney;
import us.lump.lib.util.TestEncryption;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * A JUnit class which runs all tests.
 *
 * @author Troy Bowman
 * @version $Id: TestSuite.java,v 1.11 2009/04/10 22:49:28 troy Exp $
 */
public class TestSuite extends TestCase {
  public static final String USER = "bowmantest";
  public static final String PASSWORD = "guest";
  public static final String HOST = localHost() + ":8080";
  public static final String CONTEXT = "/envelope";
  public static final LoginSettings LOGINSETTINGS = LoginSettings.getInstance();
  private static Boolean authed = null;

  static {
    BasicConfigurator.configure();

    try {
      ServerSettings ss = ServerSettings.getInstance();
      ss.setHostName(HOST);
      ss.setContext(CONTEXT);

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
   */
  private static String localHost() {
    final java.net.InetAddress localMachine;
    try {
      localMachine = java.net.InetAddress.getLocalHost();
      return localMachine.getHostName();
    } catch (UnknownHostException e) {
      return "localhost";
    }
  }

}

