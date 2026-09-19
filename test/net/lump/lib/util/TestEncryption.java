package net.lump.lib.util;

import junit.framework.TestCase;
import org.junit.Test;
import net.lump.envelope.shared.command.Command;
import net.lump.envelope.shared.command.security.Challenge;

import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.security.KeyPair;

/**
 * .
 *
 * @author Troy Bowman
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

    KeyPair kp = Encryption.generateKeyPair();
    SecretKey sessionKey = Encryption.generateSymKey();

    int startSize;
//    int buffsize = 0;
//    do {
    Command command =
      new Command(Command.Name.getChallenge, null, "troy", kp.getPublic());

    ByteArrayOutputStream serializedos = Compression.serializeOnly(command);
    startSize = serializedos.size();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    CipherOutputStream cos = Encryption.encodeSym(sessionKey, baos);
    serializedos.writeTo(cos);
    cos.flush();
//    Cipher cipherin = Cipher.getInstance(Encryption.symAlg);
//    cipherin.init(Cipher.ENCRYPT_MODE, sessionKey);

//      byte[] serializedbytes = serializedos.toByteArray();
//      for (int x = 0; x < serializedbytes.length; x += 168) {
//        int size = 168;
//        if (serializedbytes.length < (x+168)) {
//          size = serializedbytes.length - x;
//        }
//        byte[] array = new byte[size];
//        System.arraycopy(serializedbytes, x, array, 0, size);
//        if (array.length < (168))
//          baos.write(cipherin.doFinal(array));
//        else baos.write(cipherin.update(array));
//      }

//      serializedos.writeTo(cos);
//      if (buffsize>0)cos.write(new byte[buffsize]);
//      cos.flush();
//      cos.close();

//    Cipher cipherout = Cipher.getInstance(Encryption.symAlg);
//    cipherout.init(Cipher.DECRYPT_MODE, sessionKey);
//    byte[] output = cipherout.doFinal(baos.toByteArray());
//    finishSize = output.length;

    InputStream is3 = new ByteArrayInputStream(baos.toByteArray());
    CipherInputStream cis3 = Encryption.decodeSym(sessionKey, is3);
    ObjectInputStream ois = new ObjectInputStream(cis3);
    Object o = ois.readObject();

    // This used to read whatever was LEFT in the stream after readObject had
    // already consumed the whole object, and assert that count equalled the
    // serialized size: 724 against the -1 that read() answers at EOF.  It could
    // never pass.  What the test means to establish is that a Command survives
    // the symmetric round trip, so assert that instead.
    assertNotNull("nothing came back out of the cipher stream", o);
    assertTrue("round-tripped object is a " + o.getClass().getName() + ", not a Command",
      o instanceof Command);
    assertEquals("the command that came back is not the one that went in",
      Command.Name.getChallenge, ((Command)o).getName());
    assertTrue("nothing was written to the cipher stream", startSize > 0);
//      buffsize++;
//    } while (startSize != finishSize);
  }

  protected void setUp() throws Exception {
    super.setUp();
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }

}
