package net.lump.envelope;

import junit.framework.Test;
import junit.framework.TestCase;
import org.apache.log4j.BasicConfigurator;
import net.lump.envelope.client.TestQuery;
import net.lump.envelope.client.TestSecurity;
import net.lump.envelope.client.portal.SecurityPortal;
import net.lump.envelope.client.ui.prefs.LoginSettings;
import net.lump.envelope.client.ui.prefs.PrefsNode;
import net.lump.envelope.client.ui.prefs.ServerSettings;
import net.lump.envelope.shared.exception.AbortException;
import net.lump.lib.TestMoney;
import net.lump.lib.util.TestEncryption;

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
 */
public class TestSuite extends TestCase {
  // Overridable so the suite can be pointed at whatever stack is actually up --
  // the dev compose stack publishes 7041, not 8080:
  //   -Denvelope.test.host=localhost:7041 -Denvelope.test.user=guest
  public static final String USER = System.getProperty("envelope.test.user", "bowmantest");
  public static final String PASSWORD = System.getProperty("envelope.test.password", "guest");
  public static final String HOST = System.getProperty("envelope.test.host", localHost() + ":8080");
  public static final String CONTEXT = System.getProperty("envelope.test.context", "/envelope");
  public static final LoginSettings LOGINSETTINGS = LoginSettings.getInstance();
  private static Boolean authed = null;
  /** Why the handshake failed, if it did, for the tests that need one to report. */
  private static String unavailable = null;

  static {
    BasicConfigurator.configure();

    // The settings below go wherever PrefsNode says, and surefire points that at a
    // throwaway node (see envelope.prefs.node in pom.xml).  Without it these writes
    // land in the store the real Swing client reads and silently repoint it at the
    // test server as user "bowmantest".  Refuse to run rather than do that.
    if (System.getProperty(PrefsNode.PROPERTY) == null)
      throw new IllegalStateException(
          "refusing to run: " + PrefsNode.PROPERTY + " is not set, so these tests would"
          + " overwrite the real client's saved host, port and username."
          + "  Run through maven, or set -D" + PrefsNode.PROPERTY + "=<throwaway node>.");

    try {
      ServerSettings ss = ServerSettings.getInstance();
      ss.setHostName(HOST);
      ss.setContext(CONTEXT);

      LOGINSETTINGS.setUsername(USER);
      LOGINSETTINGS.setPassword(PASSWORD);

      if (!authed()) unavailable = "server at " + HOST + CONTEXT + " refused user " + USER;
    } catch (Exception e) {
      // This used to be System.exit(1), which killed the surefire fork outright:
      // the whole run died with "The forked VM terminated without properly saying
      // goodbye" and not one test -- including the several that need no server at
      // all -- ever reported a result.  Record it and let the tests that need a
      // server say so individually.
      unavailable = e.getClass().getSimpleName() + ": " + e.getMessage()
                    + " (server at " + HOST + CONTEXT + ", user " + USER + ")";
    }

    if (unavailable != null)
      System.err.println("TestSuite: no server handshake -- " + unavailable);
  }

  /**
   * Fail the calling test with the reason the handshake did not happen, if it did
   * not.  Tests that need a live server should call this first rather than dying
   * on a null session somewhere further in.
   */
  public static void requireServer() {
    if (unavailable != null) fail("no server handshake: " + unavailable);
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

