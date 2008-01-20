package us.lump.envelope.client;

import junit.framework.TestCase;
import org.junit.Test;
import us.lump.envelope.TestSuite;
import us.lump.envelope.client.portal.SecurityPortal;

public class TestSecurity extends TestCase {

  /**
   * tests a login.
   *
   * @throws Exception
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testLogin() throws Exception {
    SecurityPortal sp = new SecurityPortal();
    Boolean authed = sp.auth(
        TestSuite.loginSettings.challengeResponse(sp.getChallenge()));

    assertTrue("User does not auth", authed);

  }

  protected void setUp() throws Exception {
    super.setUp();
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }
}