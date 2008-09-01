package us.lump.lib.util;

import junit.framework.TestCase;
import org.junit.Test;
import us.lump.envelope.server.security.Challenge;

import javax.crypto.SecretKey;
import javax.crypto.CipherInputStream;
import java.security.KeyPair;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;

/**
 * .
 *
 * @author Troy Bowman
 * @version $Revision: 1.4 $
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

  @Test
  public void testSymmetric() throws Exception {
    SecretKey key = Encryption.generateSymKey();
    String message = "abc123";
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    CipherOutputStream cos = Encryption.encodeSym(key, baos);
    cos.write(message.getBytes());
    cos.flush();

    byte[] encrypted = baos.toByteArray();

    ByteArrayInputStream bis = new ByteArrayInputStream(baos.toByteArray());
    CipherInputStream cis = Encryption.decodeSym(key, bis);

    byte[] decrypted = new byte[4096];
    int read = cis.read(decrypted, 0, 4096);
    String decryptedMessage = (new String(decrypted)).substring(0,read);
    System.out.println(decryptedMessage);
  }

  protected void setUp() throws Exception {
    super.setUp();
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }

}
