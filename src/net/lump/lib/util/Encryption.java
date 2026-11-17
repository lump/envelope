package net.lump.lib.util;

import javax.crypto.*;
import java.io.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * This class has static methods and variables to standardize encryption types
 * for the app and to ease the use of java's RSA and DES public/private key
 * signing and encryption.
 *
 * @author Troy Bowman
 */

public final class Encryption {

  // encoding for getBytes and new String, to keep things consistent across VMs.
  public static final String TRANS_ENCODING = "US-ASCII";

  // RSA-3072 (0.16): the client's per-launch key signs every command after login
  // and receives the session id; the server's wraps the request body's session
  // key.  1024 bits until 0.16 -- NIST has disallowed it for signatures since 2013.
  public static final String keyAlg = "RSA";
  public static final int keyBits = 3072;

  // SHA-256 (0.16); SHA-1 until then, whose collisions are practical
  public static final String sigAlg = "SHA256with" + keyAlg;

  // digest used to bind a signature to the payload it was made for
  public static final String digestAlg = "SHA-256";

  // 168 bit triple-des for symmetric encryption
//  public static final String symAlg = "DESede";
  public static final String symKeyAlg = "DESede";
//  public static final String symAlg = symKeyAlg;// + "/ECB/PKCS5Padding";
  public static final String symAlg = symKeyAlg + "/ECB/PKCS5Padding";
  public static final int symKeyBits = 168;  //112;

  private static KeyGenerator keyGenerator = null;

  private Encryption() {
  }

  /**
   * Decodes and encrypted bytearray.
   *
   * @param key  to use
   * @param data encrypted bytearray
   *
   * @return byte[] decrypted bytearray
   *
   * @throws InvalidKeyException
   * @throws BadPaddingException
   * @throws IllegalBlockSizeException
   * @throws NoSuchAlgorithmException
   * @throws NoSuchPaddingException
   */
  public static byte[] decode(Key key, byte[] data)
      throws
      InvalidKeyException,
      BadPaddingException,
      IllegalBlockSizeException,
      NoSuchAlgorithmException,
      NoSuchPaddingException {
    final Cipher c = Cipher.getInstance(symAlg);
    c.init(Cipher.DECRYPT_MODE, key);
    return c.doFinal(data);
  }

  /**
   * Decode an asymmetrically encrypted message with a private key.
   *
   * @param key  private key
   * @param data array
   *
   * @return byte[]
   *
   * @throws InvalidKeyException
   * @throws BadPaddingException
   * @throws IllegalBlockSizeException
   * @throws NoSuchAlgorithmException
   * @throws NoSuchPaddingException
   */
  public static byte[] decodeAsym(PrivateKey key, byte[] data)
      throws
      InvalidKeyException,
      BadPaddingException,
      IllegalBlockSizeException,
      NoSuchAlgorithmException,
      NoSuchPaddingException {
    final Cipher c = Cipher.getInstance(keyAlg);
    c.init(Cipher.DECRYPT_MODE, key);
    return c.doFinal(data);
  }


  public static PublicKey decodePublicKey(String key)
      throws NoSuchAlgorithmException,
      InvalidKeySpecException,
      IOException {
    return KeyFactory.getInstance(keyAlg)
        .generatePublic(
            new X509EncodedKeySpec(Base64.base64ToByteArray(key)));
  }

  public static PrivateKey decodePrivateKey(String key)
      throws NoSuchAlgorithmException,
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
   *
   * @return byte[] encrypted data.
   *
   * @throws NoSuchAlgorithmException
   * @throws NoSuchPaddingException
   * @throws InvalidKeyException
   * @throws BadPaddingException
   * @throws IllegalBlockSizeException
   */
  public static byte[] encode(Key key, byte[] data)
      throws
      NoSuchAlgorithmException,
      NoSuchPaddingException,
      InvalidKeyException,
      BadPaddingException,
      IllegalBlockSizeException {
    final Cipher c = Cipher.getInstance(symAlg);
    c.init(Cipher.ENCRYPT_MODE, key);
    return c.doFinal(data);
  }

  /**
   * Encrypts a bytearray using the Key algorithm. The length of the message is
   * limited by the key size.
   *
   * @param key  to use
   * @param data to encode
   *
   * @return byte[]
   *
   * @throws InvalidKeyException
   * @throws NoSuchAlgorithmException
   * @throws NoSuchPaddingException
   * @throws BadPaddingException
   * @throws IllegalBlockSizeException
   */
  public static byte[] encodeAsym(PublicKey key, byte[] data)
      throws
      InvalidKeyException,
      NoSuchAlgorithmException,
      NoSuchPaddingException,
      BadPaddingException,
      IllegalBlockSizeException {
    final Cipher c = Cipher.getInstance(keyAlg);
    c.init(Cipher.ENCRYPT_MODE, key);
//    if (data.length > c.getBlockSize()) throw new IllegalBlockSizeException();
    return c.doFinal(data);
  }


  /**
   * Generates a KeyPair.
   *
   * @return KeyPair
   *
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
   *
   * @throws NoSuchAlgorithmException
   */
  public static SecretKey generateSymKey() throws NoSuchAlgorithmException {
    return getKeyGenerator().generateKey();
  }

  public static KeyGenerator getKeyGenerator() throws NoSuchAlgorithmException {
    if (keyGenerator == null) {
      keyGenerator = KeyGenerator.getInstance(symKeyAlg);
      keyGenerator.init(symKeyBits);
    }
    return keyGenerator;
  }

  /**
   * Signs a messages with a private key.
   *
   * @param key     private key
   * @param message to be signed
   *
   * @return byte[] signature bytearray
   *
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
   *
   * @return String base-64 encoded signature
   *
   * @throws NoSuchAlgorithmException
   * @throws SignatureException
   * @throws InvalidKeyException
   */
  public static String sign(PrivateKey key, String message)
      throws NoSuchAlgorithmException, SignatureException,
      InvalidKeyException, UnsupportedEncodingException {
    return Base64.byteArrayToBase64(sign(key,
                                         message.getBytes(TRANS_ENCODING)));
  }

  /**
   * Decrypts a wrapped symmetric secret Key.
   *
   * @param encryptedKey byte array
   * @param privateKey   for decryption
   *
   * @return Key secret
   *
   * @throws NoSuchAlgorithmException
   * @throws NoSuchPaddingException
   * @throws InvalidKeyException
   * @throws IOException
   */
  public static Key unwrapSessionKey(byte[] encryptedKey,
                                           PrivateKey privateKey)
      throws
      NoSuchAlgorithmException,
      NoSuchPaddingException,
      InvalidKeyException,
      IOException {
    final Cipher decKeyCipher = Cipher.getInstance(keyAlg);
    decKeyCipher.init(Cipher.UNWRAP_MODE, privateKey);
    return decKeyCipher.unwrap(encryptedKey, symKeyAlg, Cipher.SECRET_KEY);
  }

  /**
   * Verifies a bytearray signature with a private key.
   *
   * @param key       private
   * @param message   that is signed
   * @param signature to verify
   *
   * @return boolean verifiable
   *
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
   *
   * @return boolean verifiable
   *
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
        message.getBytes(TRANS_ENCODING),
        Base64.base64ToByteArray(signature)
    );
  }

  /**
   * Encrypt a secret secret key with a public key for the corresponding private
   * key to decrypt, to allow for encrypted communication.
   *
   * @param sessionKey to encrypt
   * @param publicKey  to use for the cipher
   *
   * @return byte[] encoded encrypted key
   *
   * @throws InvalidKeyException
   * @throws NoSuchAlgorithmException
   * @throws NoSuchPaddingException
   * @throws IllegalBlockSizeException
   */
  public static byte[] wrapSecretKey(Key sessionKey, PublicKey publicKey)
      throws
      InvalidKeyException,
      NoSuchAlgorithmException,
      NoSuchPaddingException,
      IllegalBlockSizeException {
    final Cipher sessionEncKeyCipher = Cipher.getInstance(keyAlg);
    sessionEncKeyCipher.init(Cipher.WRAP_MODE, publicKey);
    return sessionEncKeyCipher.wrap(sessionKey);
  }

  public static String encodeSym(SecretKey sessionKey, byte[] bytes)
      throws IllegalBlockSizeException, BadPaddingException,
      InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException {
    final Cipher c = Cipher.getInstance(symAlg);
    c.init(Cipher.ENCRYPT_MODE, sessionKey);
    return Base64.byteArrayToBase64(c.doFinal(bytes));
  }

  public static byte[] decodeSym(SecretKey sessionKey, String string)
      throws IllegalBlockSizeException, BadPaddingException,
      InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException {
    final Cipher c = Cipher.getInstance(symAlg);
    c.init(Cipher.DECRYPT_MODE, sessionKey);
    return c.doFinal(Base64.base64ToByteArray(string));
  }


  public static CipherOutputStream encodeSym(SecretKey sessionKey, OutputStream os)
      throws
      NoSuchAlgorithmException,
      NoSuchPaddingException,
      InvalidKeyException {
    return new CipherOutputStream(os, sessionKey, symAlg);
  }

  public static CipherInputStream decodeSym(SecretKey sessionKey,
                                            InputStream is)
      throws
      NoSuchAlgorithmException,
      NoSuchPaddingException,
      InvalidKeyException {
    final Cipher c = Cipher.getInstance(symAlg);
    c.init(Cipher.DECRYPT_MODE, sessionKey);
    return new CipherInputStream(is, c);
  }

  /**
   * "Freeze" (or Serialize) an serializable object into a byte array.
   *
   * @param s object
   *
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
   * "Thaw" (or Deserialize) a serializable object from a byte array. You'll
   * still have to cast the object at compile time.
   *
   * @param b byte[] array
   *
   * @return Serializable
   *
   * @throws ClassNotFoundException The class that we are instantiating isn't
   *                                found in the classpath.
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

  /**
   * A digest of arbitrary bytes, so that a signature can be bound to the exact
   * payload it was made for rather than just to a name and a timestamp.
   *
   * @param data the bytes to digest
   *
   * @return String base64 of the digest
   *
   * @throws NoSuchAlgorithmException if the digest algorithm is unavailable
   */
  public static String digest(byte[] data) throws NoSuchAlgorithmException {
    return Base64.byteArrayToBase64(MessageDigest.getInstance(digestAlg).digest(data));
  }

  /**
   * A cryptographically random opaque token, used for session identifiers.
   *
   * @return String base64 of 32 random bytes
   */
  public static String randomToken() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.byteArrayToBase64(bytes);
  }
}
