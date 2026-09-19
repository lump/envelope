package net.lump.envelope.client.ui.prefs;

import net.lump.envelope.client.portal.SecurityPortal;
import net.lump.envelope.client.ui.defs.Strings;
import net.lump.envelope.shared.command.security.Challenge;
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
    String key = encryptedPasswordKey();
    return key != null && prefs.getByteArray(key, null) != null;
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

  /**
   * Get the challenge response if it is saved.
   *
   * @return String the encrypted password to auth with the server.
   *
   * @throws IllegalStateException if the encrypted password is not saved.
   */
  public byte[] challengeResponse() {
    String key = encryptedPasswordKey();
    byte[] response = key == null ? null : prefs.getByteArray(key, null);
    if (!shouldPasswordBeSaved() || null == response || response.length == 0)
      throw new IllegalStateException("Password is not saved.");
    else
      return response;
  }

  /**
   * Get the generate a challenge response from a Challenge and a password.
   *
   * @param challenge the challenge object
   * @param password  a string which is the password
   *
   * @return a byte array which comprises the encrypted response.
   *
   * @throws NoSuchAlgorithmException
   * @throws BadPaddingException
   * @throws IOException
   * @throws IllegalBlockSizeException
   * @throws InvalidKeyException
   * @throws NoSuchPaddingException
   * @throws InvalidKeySpecException
   */
  public byte[] challengeResponse(Challenge challenge, String password)
    throws NoSuchAlgorithmException, BadPaddingException, IOException,
    IllegalBlockSizeException, InvalidKeyException,
    NoSuchPaddingException, InvalidKeySpecException {
    setPassword(password);
    return challengeResponse(challenge);
  }

  /**
   * Generate a challenge response from a Challenge and the current password.
   *
   * @param challenge the Challenge object from the server.
   *
   * @return a byte array which comprises the encrypted response.
   *
   * @throws BadPaddingException
   * @throws NoSuchAlgorithmException
   * @throws IllegalBlockSizeException
   * @throws InvalidKeyException
   * @throws NoSuchPaddingException
   * @throws IOException
   */
  public byte[] challengeResponse(Challenge challenge)
    throws BadPaddingException, NoSuchAlgorithmException,
    IllegalBlockSizeException, InvalidKeyException,
    NoSuchPaddingException, IOException {

    byte[] response = null;

    if (password == null && shouldPasswordBeSaved() && passwordIsSaved()) {
      response = prefs.getByteArray(encryptedPasswordKey(), new byte[]{});
      if (response.length == 0)
        throw new EnvelopeException(Invalid_Credentials);
      else return response;
    }

    // No password typed and nothing saved for this username on this host.  This
    // used to be an unchecked IllegalStateException, which nothing on the login
    // path catches; a failed login is what this actually is, and callers already
    // handle that.
    if (this.password == null)
      throw new EnvelopeException(Invalid_Credentials);

    // encrypt the response with the server's public key
    response = Encryption.encodeAsym(
      challenge.getServerKey(),
      Crypt.crypt(
        challenge.getChallenge(getKeyPair().getPrivate()),
        new String(
          Encryption.decodeAsym(
            getKeyPair().getPrivate(),
            this.password
          ),
          Crypt.PASSWORD_ENCODING
        )
        // the hash itself is ASCII, but pin it too rather than leave one
        // conversion on this path reading the platform default
      ).getBytes(Encryption.TRANS_ENCODING)
    );

    // response is encrypted with server's public key, so only that specific
    // server can use it, we can save it, and the saved challenge response
    // on disk cannot be decrypted to find the original password.
    String key = encryptedPasswordKey();
    if (shouldPasswordBeSaved() && key != null) prefs.putByteArray(key, response);
      // else make sure it is null
    else if (key != null) prefs.remove(key);
    // a response saved by an older build under the host-only key is a
    // password-equivalent for whoever logged in then; it will never be read again
    prefs.remove(legacyEncryptedPasswordKey());

    return response;
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
