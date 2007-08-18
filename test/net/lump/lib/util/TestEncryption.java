package us.lump.lib.util;

import junit.framework.TestCase;
import org.junit.Test;
import us.lump.envelope.server.security.Challenge;

import java.security.KeyPair;

/**
 * .
 *
 * @author Troy Bowman
 * @version $Revision: 1.3 $
 */
public class TestEncryption extends TestCase {

  @Test
  public void testEncryption() throws Exception {
    KeyPair kp = Encryption.generateKeyPair();

    String blah = "blahblah";
    byte[] encrypted =
        Encryption.encodeAsym(kp.getPublic(), blah.getBytes("US-ASCII"));
    assertEquals("Asym Decryption failed",
                 blah,
                 new String(Encryption.decodeAsym(kp.getPrivate(), encrypted),
                            "US-ASCII"));

    Challenge c = new Challenge(kp.getPublic(), kp.getPublic(), blah);
    assertEquals("Challenge decription failed",
                 c.getChallenge(kp.getPrivate()), blah);
  }

  protected void setUp() throws Exception {
    super.setUp();
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }

}
