package us.lump.envelope.client;

import junit.framework.TestCase;
import org.junit.Test;
import us.lump.envelope.TestSuite;
import us.lump.envelope.client.portal.SecurityPortal;
import us.lump.envelope.client.portal.TransactionPortal;
import us.lump.envelope.entity.Account;
import us.lump.envelope.entity.Category;
import us.lump.envelope.entity.Transaction;
import us.lump.lib.Money;

import java.sql.Date;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * Test queries.
 *
 * @author troy
 * @version $Id: TestQuery.java,v 1.4 2008/09/13 19:19:30 troy Test $
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
