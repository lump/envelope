package us.lump.lib.util;

import us.lump.lib.util.encdec.Base64Decoder;
import us.lump.lib.util.encdec.Base64Encoder;

import javax.crypto.*;
import java.io.IOException;
import java.security.*;
import static java.security.KeyFactory.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;

/**
 * This class has static methods and variables to standardize encryption types for Soar and to
 * ease the use of java's RSA and DES public/private key signing and encryption.
 *
 * @author Troy Bowman
 * @version $Id: Encryption.java,v 1.1 2007/07/21 20:15:04 troy Exp $
 */

public final class Encryption {
  // 1024 bit rsa key
  static final String keyAlg = "RSA";
  static final int keyBits = 1024;

  // 168 bit 3des symmetric encryption
  static final String symAlg = "DESede";
  static final int symBits = 168;  //112;

  private Encryption() {
  }

  /**
   * Decodes a base-64 string into a byte array.
   *
   * @param string to decode
   * @return byte[]
   * @throws IOException
   */
  public static byte[] base64dec(String string) throws IOException {
    return get64Dec().decodeBuffer(string);
  }

  /**
   * Encodes a byte array into a base-64 string.
   *
   * @param bytes to encode
   * @return String
   * @throws IOException
   */
  public static String base64enc(byte[] bytes) throws IOException {
    return get64Enc().encodeBuffer(bytes);
  }

  /**
   * Decodes and encrypted bytearray.
   *
   * @param key to use
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
   * Decodes an encrypted string.
   *
   * @param key to use
   * @param data encrypted String
   * @return String cleartext
   * @throws NoSuchAlgorithmException
   * @throws NoSuchPaddingException
   * @throws InvalidKeyException
   * @throws BadPaddingException
   * @throws IllegalBlockSizeException
   * @throws IOException
   */
  public static String decode(Key key, String data)
      throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
      BadPaddingException, IllegalBlockSizeException, IOException {
    return new String(decode(key, get64Dec().decodeBuffer(data)));
  }

  /**
   * Decode an asymmetrically encrypted message with a private key.
   *
   * @param key private key
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

  /**
   * Decode an asymmetrically encrypted message with a private key.
   *
   * @param key to use
   * @param data to decode
   * @return String
   * @throws NoSuchAlgorithmException
   * @throws NoSuchPaddingException
   * @throws InvalidKeyException
   * @throws BadPaddingException
   * @throws IllegalBlockSizeException
   * @throws IOException
   */
  public static String decodeAsym(PrivateKey key, String data)
      throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
      BadPaddingException, IllegalBlockSizeException, IOException {
    return new String(decodeAsym(key, get64Dec().decodeBuffer(data)));
  }

  /**
   * Takes a base-64 public key and converts it to a PublicKey.
   *
   * @param key base-64 string with key
   * @return PublicKey key
   * @throws NoSuchAlgorithmException
   * @throws InvalidKeySpecException
   * @throws IOException
   */
  public static PublicKey decodePublicKey(String key) throws NoSuchAlgorithmException,
      InvalidKeySpecException,
      IOException {
    return getInstance(keyAlg)
        .generatePublic(new X509EncodedKeySpec(get64Dec().decodeBuffer(key)));
  }

  /**
   * Takes a base-64 public key and converts it to a PrivateKey.
   *
   * @param key base-64 string with key
   * @return PublicKey key
   * @throws NoSuchAlgorithmException
   * @throws InvalidKeySpecException
   * @throws IOException
   */
  public static PrivateKey decodePrivateKey(String key) throws NoSuchAlgorithmException,
      InvalidKeySpecException,
      IOException {
    return getInstance(keyAlg)
        .generatePrivate(new PKCS8EncodedKeySpec(get64Dec().decodeBuffer(key)));
  }

  /**
   * Encrypts a bytearray.
   *
   * @param key    symmetric key
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
   * Encrypts a string.
   *
   * @param key    symmetric key
   * @param data clear Text
   * @return String encrypted base-64 string
   * @throws NoSuchAlgorithmException
   * @throws NoSuchPaddingException
   * @throws InvalidKeyException
   * @throws BadPaddingException
   * @throws IllegalBlockSizeException
   */
  public static String encode(Key key, String data)
      throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
      BadPaddingException, IllegalBlockSizeException {
    return get64Enc().encode(encode(key, data.getBytes()));
  }

  /**
   * Encrypts a bytearray using the Key algorithm.  The length of the message is limited by the key size.
   *
   * @param key to use
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
   * Encrypts a string using a public key.  This is limited by the key size.
   *
   * @param key to use
   * @param data to encode
   * @return String
   * @throws InvalidKeyException
   * @throws NoSuchAlgorithmException
   * @throws NoSuchPaddingException
   * @throws BadPaddingException
   * @throws IllegalBlockSizeException
   */
  public static String encodeAsym(PublicKey key, String data)
      throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException,
      BadPaddingException, IllegalBlockSizeException {
    return get64Enc().encode(encodeAsym(key, data.getBytes()));
  }

  /**
   * Encodes a public key into a base-64 string.
   *
   * @param pk public key
   * @return String base-64 key
   */
  public static String encodeKey(Key pk) {
    return get64Enc().encode(pk.getEncoded());
  }

  /**
   * Yanks a public key from a KeyPair and encodes it in base-64.
   *
   * @param kp keypair
   * @return String base-64 key
   */
  public static String encodePublicKey(KeyPair kp) {
    return encodeKey(kp.getPublic());
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
   * Generates a symmetric Key.
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
    final Signature sig =
        Signature.getInstance(keyAlg.equals("RSA") ? "MD5with" + keyAlg : keyAlg);
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
      throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
    return get64Enc().encode(sign(key, message.getBytes()));
  }

  /**
   * Decrypts a wrapped symmetric secret Key.
   *
   * @param encryptedKey wrapped
   * @param privateKey   for decryption
   * @return Key secret
   * @throws NoSuchAlgorithmException
   * @throws NoSuchPaddingException
   * @throws InvalidKeyException
   * @throws IOException
   */
  public static Key unwrapSessionKey(String encryptedKey, PrivateKey privateKey)
      throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IOException {
    final Cipher decKeyCipher = Cipher.getInstance(keyAlg);
    decKeyCipher.init(Cipher.UNWRAP_MODE, privateKey);

    return decKeyCipher.unwrap(get64Dec().decodeBuffer(encryptedKey), symAlg, Cipher.SECRET_KEY);
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
    final Signature sig =
        Signature.getInstance(keyAlg.equals("RSA") ? "MD5with" + keyAlg : keyAlg);
    sig.initVerify(key);
    sig.update(message);
    return sig.verify(signature);
  }

  /**
   * Verifies a base-64 encoded signature of a message with a private key.
   *
   * @param key       private
   * @param message   that is signed
   * @param signature to verify
   * @return boolean verifiable
   * @throws InvalidKeySpecException
   * @throws NoSuchAlgorithmException
   * @throws InvalidKeyException
   * @throws SignatureException
   * @throws IOException
   */
  public static boolean verify(String key, String message, String signature)
      throws
      InvalidKeySpecException,
      NoSuchAlgorithmException,
      InvalidKeyException,
      SignatureException,
      IOException {
    final KeyFactory keyFactory = getInstance(keyAlg);
    return verify(
        keyFactory.generatePublic(new X509EncodedKeySpec(get64Dec().decodeBuffer(key))),
        message.getBytes(),
        get64Dec().decodeBuffer(signature)
    );
  }

  /**
   * Verifies a base-64 encoded signature of a message with a private key.
   *
   * @param key       private
   * @param message   that is signed
   * @param signature to verify
   * @return boolean verifiable
   * @throws InvalidKeySpecException
   * @throws NoSuchAlgorithmException
   * @throws InvalidKeyException
   * @throws SignatureException
   * @throws IOException
   */
  public static boolean verify(PublicKey key, String message, String signature)
      throws
      InvalidKeySpecException,
      NoSuchAlgorithmException,
      InvalidKeyException,
      SignatureException,
      IOException {
    return verify(
        key,
        message.getBytes(),
        get64Dec().decodeBuffer(signature)
    );
  }

  /**
   * Encrypt a secret session key with a public key for a private key to decrypt.
   *
   * @param sessionKey to encrypt
   * @param publicKey  to use for the cipher
   * @return String a base-64 encoded encrypted key
   * @throws NoSuchAlgorithmException
   * @throws NoSuchPaddingException
   * @throws InvalidKeyException
   * @throws IllegalBlockSizeException
   */
  public static String wrapSessionKey(Key sessionKey, PublicKey publicKey)
      throws
      NoSuchAlgorithmException,
      NoSuchPaddingException,
      InvalidKeyException,
      IllegalBlockSizeException {
    final Cipher sessionEncKeyCipher = Cipher.getInstance(keyAlg);
    sessionEncKeyCipher.init(Cipher.WRAP_MODE, publicKey);
    return get64Enc().encode(sessionEncKeyCipher.wrap(sessionKey));
  }

  private static Base64Decoder get64Dec() {
    return new Base64Decoder();
  }

  private static Base64Encoder get64Enc() {
    return new Base64Encoder();
  }
}
