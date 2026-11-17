package net.lump.envelope.client;

import junit.framework.TestCase;
import org.junit.Test;
import net.lump.envelope.TestSuite;
import net.lump.envelope.client.portal.SecurityPortal;

public class TestSecurity extends TestCase {

  /**
   * tests a login.
   *
   * @throws Exception
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testLogin() throws Exception {
    Boolean authed = new SecurityPortal().login();

    assertTrue("User does not auth", authed);

  }

  protected void setUp() throws Exception {
    super.setUp();
    TestSuite.requireServer();
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }
}