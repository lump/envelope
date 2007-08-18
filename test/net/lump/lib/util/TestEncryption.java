package us.lump.lib.util;

import junit.framework.TestCase;
import us.lump.envelope.server.security.Challenge;

import java.security.KeyPair;

/**
 * .
 *
 * @author Troy Bowman
 * @version $Revision: 1.1 $
 */
public class TestEncryption extends TestCase {

  public void TestEncryption() throws Exception {
    KeyPair kp = Encryption.generateKeyPair();

    String blah = "blahblah";
    byte[] encrypted = Encryption.encodeAsym(kp.getPublic(), blah.getBytes("US-ASCII"));
    assertEquals("Asym Decryption failed",
            blah,
            new String(Encryption.decodeAsym(kp.getPrivate(), encrypted), "US-ASCII"));

    Challenge c = new Challenge(kp.getPublic(), kp.getPublic(), blah);
    assertEquals("Challenge decription failed",
            c.getChallenge(kp.getPrivate()), blah);
  }
}
