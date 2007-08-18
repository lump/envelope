package us.lump.lib.util;

import javax.crypto.*;
import java.io.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * This class has static methods and variables to standardize encryption types for Soar and to
 * ease the use of java's RSA and DES public/private key signing and encryption.
 *
 * @author Troy Bowman
 * @version $Id: Encryption.java,v 1.2 2007/08/18 04:49:44 troy Exp $
 */

public final class Encryption {
  public static final String ENCODING = "US-ASCII";

  // 1024 bit rsa key
  static final String keyAlg = "RSA";
  static final int keyBits = 1024;

  // signature algorithm
  static final String sigAlg = "SHA1with" + keyAlg;

  // 168 bit 3des symmetric encryption
  static final String symAlg = "DESede";
  static final int symBits = 168;  //112;

  private Encryption() {
  }

  /**
   * Decodes and encrypted bytearray.
   *
   * @param key  to use
   * @param data encrypted bytearray
   * @return byte[] decrypted bytearray
   * @throws InvalidKeyException
   * @throws BadPaddingException
   * @throws IllegalBlockSizeException
   * @throws NoSuchAlgorithmException
   * @throws NoSuchPaddingException
   */
  public static byte[] decode(Key key, byte[] data)
          throws InvalidKeyException, BadPaddingException, IllegalBlockSizeException,
          NoSuchAlgorithmException, NoSuchPaddingException {
    final Cipher c = Cipher.getInstance(symAlg);
    c.init(Cipher.DECRYPT_MODE, key);
    return c.doFinal(data);
  }

  /**
   * Decode an asymmetrically encrypted message with a private key.
   *
   * @param key  private key
   * @param data array
   * @return byte[]
   * @throws InvalidKeyException
   * @throws BadPaddingException
   * @throws IllegalBlockSizeException
   * @throws NoSuchAlgorithmException
   * @throws NoSuchPaddingException
   */
  public static byte[] decodeAsym(PrivateKey key, byte[] data)
          throws InvalidKeyException, BadPaddingException, IllegalBlockSizeException,
          NoSuchAlgorithmException, NoSuchPaddingException {
    final Cipher c = Cipher.getInstance(keyAlg);
    c.init(Cipher.DECRYPT_MODE, key);
    return c.doFinal(data);
  }

  public static PublicKey decodePublicKey(String key) throws NoSuchAlgorithmException,
          InvalidKeySpecException,
          IOException {
    return KeyFactory.getInstance(keyAlg)
            .generatePublic(
                    new X509EncodedKeySpec(Base64.base64ToByteArray(key)));
  }

  public static PrivateKey decodePrivateKey(String key) throws NoSuchAlgorithmException,
          InvalidKeySpecException,
          IOException {
    return KeyFactory.getInstance(keyAlg)
            .generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.base64ToByteArray(key)));
  }

  public static String encodeKey(Key key) {
    return Base64.byteArrayToBase64(key.getEncoded());
  }


  /**
   * Encrypts a bytearray.
   *
   * @param key  symmetric key
   * @param data bytarray of data
   * @return byte[] encrypted data.
   * @throws NoSuchAlgorithmException
   * @throws NoSuchPaddingException
   * @throws InvalidKeyException
   * @throws BadPaddingException
   * @throws IllegalBlockSizeException
   */
  public static byte[] encode(Key key, byte[] data)
          throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
          BadPaddingException, IllegalBlockSizeException {
    final Cipher c = Cipher.getInstance(symAlg);
    c.init(Cipher.ENCRYPT_MODE, key);
    return c.doFinal(data);
  }

  /**
   * Encrypts a bytearray using the Key algorithm.
   * The length of the message is limited by the key size.
   *
   * @param key  to use
   * @param data to encode
   * @return byte[]
   * @throws InvalidKeyException
   * @throws NoSuchAlgorithmException
   * @throws NoSuchPaddingException
   * @throws BadPaddingException
   * @throws IllegalBlockSizeException
   */
  public static byte[] encodeAsym(PublicKey key, byte[] data)
          throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException,
          BadPaddingException, IllegalBlockSizeException {
    final Cipher c = Cipher.getInstance(keyAlg);
    c.init(Cipher.ENCRYPT_MODE, key);
    return c.doFinal(data);
  }

  /**
   * Generates a KeyPair.
   *
   * @return KeyPair
   * @throws NoSuchAlgorithmException
   */
  public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance(keyAlg);
    kpg.initialize(keyBits);
    return kpg.generateKeyPair();
  }

  /**
   * Generates a symmetric (secret) Key.
   *
   * @return Key
   * @throws NoSuchAlgorithmException
   */
  public static Key generateSymKey() throws NoSuchAlgorithmException {
    final KeyGenerator kg = KeyGenerator.getInstance(symAlg);
    kg.init(symBits);
    return kg.generateKey();
  }

  /**
   * Signs a messages with a private key.
   *
   * @param key     private key
   * @param message to be signed
   * @return byte[] signature bytearray
   * @throws NoSuchAlgorithmException
   * @throws InvalidKeyException
   * @throws SignatureException
   */
  public static byte[] sign(PrivateKey key, byte[] message)
          throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
    final Signature sig = Signature.getInstance(sigAlg);
    sig.initSign(key);
    sig.update(message);
    return sig.sign();
  }

  /**
   * Signs a message with private key, returning a base-64 signature.
   *
   * @param key     private key
   * @param message to be signed
   * @return String base-64 encoded signature
   * @throws NoSuchAlgorithmException
   * @throws SignatureException
   * @throws InvalidKeyException
   */
  public static String sign(PrivateKey key, String message)
          throws NoSuchAlgorithmException, SignatureException,
          InvalidKeyException, UnsupportedEncodingException {
    return Base64.byteArrayToBase64(sign(key, message.getBytes(ENCODING)));
  }

  /**
   * Decrypts a wrapped symmetric secret Key.
   *
   * @param encryptedKey byte array
   * @param privateKey   for decryption
   * @return Key secret
   * @throws NoSuchAlgorithmException
   * @throws NoSuchPaddingException
   * @throws InvalidKeyException
   * @throws IOException
   */
  public static Key unwrapSessionKey(byte[] encryptedKey, PrivateKey privateKey)
          throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IOException {
    final Cipher decKeyCipher = Cipher.getInstance(keyAlg);
    decKeyCipher.init(Cipher.UNWRAP_MODE, privateKey);

    return decKeyCipher.unwrap(encryptedKey, keyAlg, Cipher.SECRET_KEY);
  }

  /**
   * Verifies a bytearray signature with a private key.
   *
   * @param key       private
   * @param message   that is signed
   * @param signature to verify
   * @return boolean verifiable
   * @throws NoSuchAlgorithmException
   * @throws InvalidKeyException
   * @throws SignatureException
   */
  public static boolean verify(PublicKey key, byte[] message, byte[] signature)
          throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
    final Signature sig = Signature.getInstance(sigAlg);
    sig.initVerify(key);
    sig.update(message);
    return sig.verify(signature);
  }

  /**
   * Overloaded verify of byte[] to take string
   *
   * @param key       private
   * @param message   that is signed
   * @param signature to verify
   * @return boolean verifiable
   * @throws NoSuchAlgorithmException
   * @throws InvalidKeyException
   * @throws SignatureException
   * @throws UnsupportedEncodingException
   */
  public static boolean verify(PublicKey key, String message, String signature)
          throws NoSuchAlgorithmException, InvalidKeyException,
          SignatureException, UnsupportedEncodingException {
    return verify(
            key,
            message.getBytes(ENCODING),
            Base64.base64ToByteArray(signature)
    );
  }

  /**
   * Encrypt a secret secret key with a public key for the corresponding
   * private key to decrypt, to allow for encrypted communication.
   *
   * @param sessionKey to encrypt
   * @param publicKey  to use for the cipher
   * @return byte[] encoded encrypted key
   * @throws InvalidKeyException
   * @throws NoSuchAlgorithmException
   * @throws NoSuchPaddingException
   * @throws IllegalBlockSizeException
   */
  public static byte[] wrapSecretKey(Key sessionKey, PublicKey publicKey)
          throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException {
    final Cipher sessionEncKeyCipher = Cipher.getInstance(keyAlg);
    sessionEncKeyCipher.init(Cipher.WRAP_MODE, publicKey);
    return sessionEncKeyCipher.wrap(sessionKey);
  }

  /**
   * "Freeze" (or Serialize) an serializable object into a byte array.
   *
   * @param s object
   * @return byte[] array
   */
  public static byte[] freeze(Serializable s) {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      (new ObjectOutputStream(baos)).writeObject(s);
    } catch (IOException e) {
      // this should never happen, all IO is in memory...
      e.printStackTrace();
    }
    return baos.toByteArray();
  }

  /**
   * "Thaw" (or Deserialize) a serializable object from a byte array.
   * You'll still have to cast the object at compile time.
   *
   * @param b byte[] array
   * @return Serializable
   * @throws ClassNotFoundException The class that we are instantiating
   *                                isn't found in the classpath.
   */
  public static Serializable thaw(byte[] b)
          throws ClassNotFoundException {
    try {
      return (Serializable)
              new ObjectInputStream(new ByteArrayInputStream(b)).readObject();
    } catch (IOException e) {
      // this should never happen, all IO is in memory...
      e.printStackTrace();
    }

    // we should never get here, but just so java is happy...
    return null;
  }
}
