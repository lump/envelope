package us.lump.lib.util;

import junit.framework.TestCase;
import org.junit.Test;
import us.lump.envelope.shared.command.Command;
import us.lump.envelope.shared.command.security.Challenge;

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
 * @version $Id: TestEncryption.java,v 1.10 2009/07/13 17:21:44 troy Exp $
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
    int finishSize;
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
    byte[] b3 = new byte[1024];
    finishSize = cis3.read(b3);
    assertEquals("startSize doesn't match finishSize!", startSize, finishSize);
    System.out.println(startSize + " " + finishSize);
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
