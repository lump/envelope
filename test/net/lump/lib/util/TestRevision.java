package net.lump.lib.util;

import org.junit.Test;
import junit.framework.TestCase;

/**
 * Test Revision.
 *
 * @author Troy Bowman
 * @version $Id: TestRevision.java,v 1.2 2009/10/02 22:06:23 troy Exp $
 */

public class TestRevision extends TestCase {
  @Test
  public void testExistence() {
    for (Revision r : Revision.values()) {
      if (r != Revision.Name) {
        assertTrue(r + " is zero length", r.value().length() > 0);
      }
    }
  }

  public void testDate() {
    assertNotNull(Revision.Date.getDate());

    for (Revision r : Revision.values()) {
      if (r.isDate()) assertNotNull(r.getDate());
      else {
        try {
          r.getDate();
        } catch (Exception e) {
          assertTrue("Exception must be Illegal State",
                     e instanceof IllegalStateException);
        }
      }
    }
  }

  public void testPretty() {
    for (Revision r : Revision.values()) {
      if (r.value() != null) {
        assertTrue(r + " has underscores", !r.prettyValue().matches("_"));
      }
    }
  }
}
