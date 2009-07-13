package us.lump.envelope.client;

import junit.framework.TestCase;
import org.junit.Test;
import us.lump.envelope.TestSuite;
import us.lump.envelope.client.portal.SecurityPortal;

/**
 * Test queries.
 *
 * @author troy
 * @version $Id: TestQuery.java,v 1.5 2009/07/13 17:21:44 troy Exp $
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
    TestSuite.authed();
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }
}
