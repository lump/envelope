package net.lump.envelope.client.ui.prefs;

import net.lump.envelope.client.portal.SecurityPortal;
import net.lump.envelope.client.ui.defs.Strings;
import net.lump.envelope.shared.command.security.Scram;
import net.lump.envelope.shared.command.security.ScramChallenge;
import net.lump.envelope.shared.command.security.Crypt;
import net.lump.envelope.shared.exception.AbortException;
import net.lump.envelope.shared.exception.EnvelopeException;
import static net.lump.envelope.shared.exception.EnvelopeException.Name.Invalid_Credentials;
import net.lump.lib.util.Encryption;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.prefs.Preferences;

/**
 * Singleton for keeping track of login information.  (Basically the username and password.
 *
 * @author Troy Bowman
 */
public class LoginSettings {

  // these strings define preference keys.
  private static final String USER = "username";
  private static final String SHOULD_SAVE_ENCRYPTED_PASSWORD
    = "saveEncrypedPassword?";
  private static final String ENCRYPTED_PASSWORD = "encryptedPassword";
  public static final String PASSWORD_ALREADY_SET = "-password-already-set-";

  // preferences reference, defined at instantiation of the singleton.
  private Preferences prefs = PrefsNode.of(this.getClass());

  // the key-pair, generated once at instantiation of the singleton.
  private KeyPair keyPair;
  private PublicKey serverKey = null;

  // The session id the server issues at login.  Memory only -- never written to
  // prefs, so it dies with the process, which is also the lifetime of the keypair
  // it is bound to.
  private String sessionId = null;

  transient private byte[] password;

  // the singleton
  private static LoginSettings singleton;

  {
    new Thread(new Runnable() {
      public void run() {
        try {
          keyPair = Encryption.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
          e.printStackTrace();
          System.exit(1);
        }
      }
    }, Strings.get("generating.keypair")).start();
  }


  private LoginSettings() { }

  public static LoginSettings getInstance() {
    if (singleton == null) singleton = new LoginSettings();
    return singleton;
  }

  public String getUsername() {
    return prefs.get(USER, null);
  }

  public String getPassword() {
    if (passwordIsSaved()) return PASSWORD_ALREADY_SET;
    else return "";
  }

  public KeyPair getKeyPair() {
    while (keyPair == null) try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    return keyPair;
  }


  /**
   * The preference key the saved challenge response is stored under.
   *
   * <p>This used to be keyed on the host alone, which made the saved blob a
   * credential for whoever typed a username next.  After user A saved a password,
   * typing B's username -- or just leaving the pre-filled placeholder in place, which
   * makes setPassword a no-op and leaves this the only response available -- replayed
   * A's response under B's name.  The server hashed it against B's salt, refused it as
   * Invalid_Credentials, and B's real password was discarded on every attempt until
   * "remember password" was unchecked.  The response is a password-equivalent for one
   * specific account on one specific server, so the key has to name both.
   *
   * @return the key, or null if there is no username to key on
   */
  private String encryptedPasswordKey() {
    String user = getUsername();
    if (user == null || user.isEmpty()) return null;
    String key = ENCRYPTED_PASSWORD + "." + ServerSettings.getInstance().getHostName() + "." + user;
    // java.util.prefs refuses a key over Preferences.MAX_KEY_LENGTH
    if (key.length() > 80)
      key = ENCRYPTED_PASSWORD + "." + Integer.toHexString(
          (ServerSettings.getInstance().getHostName() + "." + user).hashCode());
    return key;
  }

  /**
   * The pre-{@link #encryptedPasswordKey} host-only key, so a saved response from an
   * older build can be cleared out rather than left lying in the prefs store as a
   * password-equivalent nothing will ever use again.
   *
   * @return the legacy key
   */
  private String legacyEncryptedPasswordKey() {
    return ENCRYPTED_PASSWORD + "." + ServerSettings.getInstance().getHostName();
  }

  public Boolean shouldPasswordBeSaved() {
    return prefs.getBoolean(SHOULD_SAVE_ENCRYPTED_PASSWORD, Boolean.FALSE);
  }

  public Boolean passwordIsSaved() {
    return remembered() != null;
  }

  public LoginSettings setPassword(String password)
    throws BadPaddingException, NoSuchAlgorithmException,
    IllegalBlockSizeException, InvalidKeyException,
    NoSuchPaddingException {
    // keep the password from being plain text in memory...
    if (!password.equals(PASSWORD_ALREADY_SET))
      this.password = Encryption.encodeAsym(
        getKeyPair().getPublic(),
        password.getBytes(Crypt.PASSWORD_ENCODING));
    return this;
  }

  public LoginSettings setPasswordShouldBeSaved(Boolean flag) {
    prefs.putBoolean(SHOULD_SAVE_ENCRYPTED_PASSWORD, flag);
    // reset the password if we're setting it to false
    if (!flag) {
      String key = encryptedPasswordKey();
      if (key != null) prefs.remove(key);
      prefs.remove(legacyEncryptedPasswordKey());
    }
    return this;
  }

  public LoginSettings setUsername(String username) {
    prefs.put(USER, username);
    return this;
  }

  // ------------------------------------------------------------------------
  // The keys a SCRAM login proves with (see Scram, and SecurityPortal.login).
  //
  // "Remember password" keeps ClientKey and ServerKey for this user on this
  // server, with the salts and iteration count they were made under, rather than
  // the password.  They log in to that one account on that one server -- as the
  // remembered response did before 0.14 -- and to recover the password from them
  // means guessing at the full PBKDF2 cost per guess.  They are saved only after
  // the server has accepted the proof and proven itself, where the old response
  // was saved before anyone knew whether the password was right.  If the server
  // changes the salt or count (a new password), they no longer match and the
  // password is asked for.  A blob in the pre-0.14 shape does not parse and is
  // treated as nothing remembered.
  // ------------------------------------------------------------------------

  /**
   * ClientKey and ServerKey for this challenge: from the password just typed,
   * else from what was remembered for it.
   *
   * @return {clientKey, serverKey}
   * @throws EnvelopeException Invalid_Credentials when there is neither
   */
  public byte[][] keysFor(ScramChallenge challenge) throws EnvelopeException {
    try {
      if (password != null) {
        String typed = new String(Encryption.decodeAsym(getKeyPair().getPrivate(), password), Crypt.PASSWORD_ENCODING);
        byte[] salted = Scram.saltedPassword(Crypt.crypt(challenge.md5Salt(), typed), challenge.salt(), challenge.iterations());
        return new byte[][]{Scram.clientKey(salted), Scram.serverKey(salted)};
      }
      byte[][] remembered = remembered();
      if (remembered != null && shouldPasswordBeSaved()
          && challenge.md5Salt().equals(new String(remembered[0], java.nio.charset.StandardCharsets.US_ASCII))
          && java.util.Arrays.equals(challenge.salt(), remembered[1])
          && challenge.iterations() == java.nio.ByteBuffer.wrap(remembered[2]).getInt())
        return new byte[][]{remembered[3], remembered[4]};
    } catch (java.security.GeneralSecurityException e) {
      throw new EnvelopeException(Invalid_Credentials);
    }
    throw new EnvelopeException(Invalid_Credentials);
  }

  /** Keep the keys a login just proved with, if the user asked for that; drop them if not. */
  public void remember(ScramChallenge challenge, byte[][] keys) {
    String key = encryptedPasswordKey();
    if (key == null) return;
    if (!shouldPasswordBeSaved()) {
      prefs.remove(key);
      return;
    }
    java.util.Base64.Encoder b64 = java.util.Base64.getEncoder();
    String blob = "scram1:" + b64.encodeToString(challenge.md5Salt().getBytes(java.nio.charset.StandardCharsets.US_ASCII))
        + ":" + b64.encodeToString(challenge.salt())
        + ":" + b64.encodeToString(java.nio.ByteBuffer.allocate(4).putInt(challenge.iterations()).array())
        + ":" + b64.encodeToString(keys[0]) + ":" + b64.encodeToString(keys[1]);
    prefs.putByteArray(key, blob.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    // a response saved by an older build under the host-only key is a
    // password-equivalent for whoever logged in then; it will never be read again
    prefs.remove(legacyEncryptedPasswordKey());
  }

  /** @return {md5Salt, salt, iterations, clientKey, serverKey}, or null if nothing usable is remembered */
  private byte[][] remembered() {
    String key = encryptedPasswordKey();
    byte[] stored = key == null ? null : prefs.getByteArray(key, null);
    if (stored == null) return null;
    String[] parts = new String(stored, java.nio.charset.StandardCharsets.US_ASCII).split(":");
    if (parts.length != 6 || !"scram1".equals(parts[0])) return null;
    try {
      byte[][] out = new byte[5][];
      for (int i = 0; i < 5; i++) out[i] = java.util.Base64.getDecoder().decode(parts[i + 1]);
      return out;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public PublicKey getServerKey() throws AbortException {
    // if we don't have the serverKey saved already (from a challenge or this)
    if (serverKey == null)
      setServerKey((new SecurityPortal()).getServerPublicKey());
    return serverKey;
  }

  public void setServerKey(PublicKey serverKey) {
    this.serverKey = serverKey;
  }

  /**
   * The current session id, or null if we have not logged in yet.
   *
   * @return String
   */
  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

}
