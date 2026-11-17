package net.lump.envelope.shared.command.security;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * SCRAM-SHA-256 (RFC 5802, with SHA-256 per RFC 7677): the login exchange.
 *
 * <p>The client proves it knows the password without sending it or anything a
 * listener could replay, the server stores nothing a thief could log in with,
 * and the server proves back that it holds the user's verifier -- so an impostor
 * cannot complete a login either.  JDK primitives only: PBKDF2-HMAC-SHA-256,
 * HMAC-SHA-256 and SHA-256.
 *
 * <p>The derivations are RFC 5802's, and {@code TestScram} checks them against
 * RFC 7677's test vector:
 * <pre>
 *   SaltedPassword  = PBKDF2-HMAC-SHA-256(password, salt, iterations)
 *   ClientKey       = HMAC(SaltedPassword, "Client Key")
 *   StoredKey       = SHA-256(ClientKey)          -- stored by the server
 *   ServerKey       = HMAC(SaltedPassword, "Server Key")  -- stored by the server
 *   ClientSignature = HMAC(StoredKey, AuthMessage)
 *   ClientProof     = ClientKey XOR ClientSignature  -- sent by the client
 *   ServerSignature = HMAC(ServerKey, AuthMessage)   -- sent by the server
 * </pre>
 * The server recovers ClientKey from the proof and checks its hash against
 * StoredKey.  StoredKey alone cannot make a proof, so a copy of the users table
 * does not log anyone in; it can only be attacked by guessing passwords at the
 * full PBKDF2 cost per guess.
 *
 * <p>Two things are this application's own.  The "password" fed to PBKDF2 is
 * the md5-crypt of the password under the user's md5-crypt salt -- what the
 * server stored before 0.14 -- so every existing user was converted on the
 * server without a password reset (see {@code PasswordMigration}); PBKDF2's
 * iterations carry the cost either way.  And {@link #authMessage} is not RFC
 * 5802's text: there is no SASL framing here, and it also covers the client's
 * per-launch public key, so the session the login opens is bound to the client
 * that proved itself, as it was before.
 *
 * @author troy
 */
public final class Scram {

  /** OWASP's 2023 figure for PBKDF2-HMAC-SHA-256; the client pays it once per login. */
  public static final int ITERATIONS = 600_000;
  /** below this a stored verifier is refused as too cheap to guess against */
  public static final int MIN_ITERATIONS = 100_000;
  /** above this a stored verifier is refused as a way to make every login hang */
  public static final int MAX_ITERATIONS = 10_000_000;
  public static final int KEY_BYTES = 32;
  public static final int SALT_BYTES = 16;
  public static final int NONCE_BYTES = 24;

  private static final SecureRandom random = new SecureRandom();

  private Scram() {}

  /** The verifier the server stores for a user: what a password change or a new user sends. */
  public static final class Verifier implements java.io.Serializable {
    public final String md5Salt;
    public final byte[] salt;
    public final int iterations;
    public final byte[] storedKey;
    public final byte[] serverKey;

    public Verifier(String md5Salt, byte[] salt, int iterations, byte[] storedKey, byte[] serverKey) {
      this.md5Salt = md5Salt;
      this.salt = salt;
      this.iterations = iterations;
      this.storedKey = storedKey;
      this.serverKey = serverKey;
    }
  }

  /**
   * A fresh verifier for a password: a new md5-crypt salt, a new SCRAM salt, the
   * current iteration count.  Computed on the client; only the result travels.
   */
  public static Verifier newVerifier(String password) throws GeneralSecurityException {
    // the bare eight characters, as yankSalt gives a stored hash's: one shape for
    // every salt, so a made-up one for an unknown user cannot be told apart
    String md5Salt = Crypt.yankSalt(Crypt.generateNewMd5Salt());
    byte[] salt = randomBytes(SALT_BYTES);
    byte[] salted = saltedPassword(Crypt.crypt(md5Salt, password), salt, ITERATIONS);
    return new Verifier(md5Salt, salt, ITERATIONS, storedKey(clientKey(salted)), serverKey(salted));
  }

  /** The verifier for a password already in md5-crypt form, as the server held it before 0.14. */
  public static Verifier fromMd5Crypt(String md5Crypt) throws GeneralSecurityException {
    byte[] salt = randomBytes(SALT_BYTES);
    byte[] salted = saltedPassword(md5Crypt, salt, ITERATIONS);
    return new Verifier(Crypt.yankSalt(md5Crypt), salt, ITERATIONS, storedKey(clientKey(salted)), serverKey(salted));
  }

  public static byte[] saltedPassword(String password, byte[] salt, int iterations) throws GeneralSecurityException {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BYTES * 8);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } finally {
      spec.clearPassword();
    }
  }

  public static byte[] clientKey(byte[] saltedPassword) throws GeneralSecurityException {
    return hmac(saltedPassword, "Client Key".getBytes(StandardCharsets.US_ASCII));
  }

  public static byte[] serverKey(byte[] saltedPassword) throws GeneralSecurityException {
    return hmac(saltedPassword, "Server Key".getBytes(StandardCharsets.US_ASCII));
  }

  public static byte[] storedKey(byte[] clientKey) throws GeneralSecurityException {
    return MessageDigest.getInstance("SHA-256").digest(clientKey);
  }

  /**
   * Everything both ends sign: who, both nonces, the salt and iteration count the
   * server named, and the client key the session will be bound to.  Built the
   * same way on both ends from values each already has.
   */
  public static byte[] authMessage(String username, String clientNonce, String nonce, byte[] salt,
                                   int iterations, PublicKey clientPublicKey) {
    String m = "n=" + username + ",r=" + clientNonce + ",r=" + nonce + ",s=" + b64(salt) + ",i=" + iterations
               + ",k=" + b64(clientPublicKey.getEncoded());
    return m.getBytes(StandardCharsets.UTF_8);
  }

  /** The client's proof for this exchange. */
  public static byte[] clientProof(byte[] clientKey, byte[] authMessage) throws GeneralSecurityException {
    return xor(clientKey, hmac(storedKey(clientKey), authMessage));
  }

  /** The server's check of a proof against the stored key: in constant time. */
  public static boolean proofIsValid(byte[] storedKey, byte[] authMessage, byte[] proof) throws GeneralSecurityException {
    if (storedKey == null || proof == null || proof.length != storedKey.length) return false;
    byte[] clientKey = xor(proof, hmac(storedKey, authMessage));
    return MessageDigest.isEqual(storedKey(clientKey), storedKey);
  }

  public static byte[] serverSignature(byte[] serverKey, byte[] authMessage) throws GeneralSecurityException {
    return hmac(serverKey, authMessage);
  }

  /** The client's check that the server holds this user's verifier: in constant time. */
  public static boolean serverIsGenuine(byte[] serverKey, byte[] authMessage, byte[] signature) throws GeneralSecurityException {
    return signature != null && MessageDigest.isEqual(serverSignature(serverKey, authMessage), signature);
  }

  public static String newNonce() {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(NONCE_BYTES));
  }

  public static byte[] randomBytes(int n) {
    byte[] b = new byte[n];
    random.nextBytes(b);
    return b;
  }

  public static byte[] hmac(byte[] key, byte[] message) throws GeneralSecurityException {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(message);
  }

  private static byte[] xor(byte[] a, byte[] b) {
    byte[] out = new byte[a.length];
    for (int i = 0; i < a.length; i++) out[i] = (byte)(a[i] ^ b[i]);
    return out;
  }

  private static String b64(byte[] b) {
    return Base64.getEncoder().encodeToString(b);
  }
}
