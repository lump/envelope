package net.lump.envelope.client;

import junit.framework.TestCase;
import org.junit.Test;
import net.lump.envelope.TestSuite;
import net.lump.envelope.client.portal.SecurityPortal;

/**
 * Test queries.
 *
 * @author troy
 */
public class TestQuery extends TestCase {

  // pings
  @Test public void testPing() throws Exception {
    assertTrue("Couldn't do authed ping", new SecurityPortal().rawPing());
  }

  @Test public void testAuthedPing() throws Exception {
    assertTrue("Couldn't do authed ping", new SecurityPortal().authedPing());
  }

  protected void setUp() throws Exception {
    super.setUp();
    // Say why up front.  Without this these tests carried on past a failed
    // handshake and blocked on a Swing login dialog, which is what made the suite
    // hang rather than report.
    TestSuite.requireServer();
    TestSuite.authed();
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }
}
