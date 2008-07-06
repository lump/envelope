package us.lump.envelope.client.ui.prefs;

import us.lump.envelope.server.security.Challenge;
import us.lump.envelope.server.security.Crypt;
import us.lump.envelope.server.exception.SessionException;
import us.lump.lib.util.Encryption;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.prefs.Preferences;
import java.util.Arrays;

/**
 * Singleton for keeping track of login information.  (Basically the username
 * and password.
 *
 * @author Troy Bowman
 * @version $Id: LoginSettings.java,v 1.7 2008/07/06 04:14:24 troy Exp $
 */
public class LoginSettings {

  // these strings define preference keys.
  private static final String USER = "username";
  private static final String SHOULD_SAVE_ENCRYPTED_PASSWORD
      = "saveEncrypedPassword?";
  private static final String ENCRYPTED_PASSWORD = "encryptedPassword";
  public static final String PASSWORD_ALREADY_SET = "-password-already-set-";

  // preferences reference, defined at instantiation of the singleton.
  private Preferences prefs = Preferences.userNodeForPackage(this.getClass());

  // the key-pair, generated once at instantiation of the singleton.
  private KeyPair keyPair;

  transient private byte[] password;

  // the singleton
  private static LoginSettings singleton;

  {
    try {
      keyPair = Encryption.generateKeyPair();

    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
      System.exit(1);
    }
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
    return keyPair;
  }

  public Boolean shouldPasswordBeSaved() {
    return prefs.getBoolean(SHOULD_SAVE_ENCRYPTED_PASSWORD, Boolean.FALSE);
  }

  public Boolean passwordIsSaved() {
    return (prefs.getByteArray(ENCRYPTED_PASSWORD, null) != null);
  }

  public LoginSettings setPassword(String password)
      throws BadPaddingException, NoSuchAlgorithmException,
      IllegalBlockSizeException, InvalidKeyException,
      NoSuchPaddingException {
    // keep the password from being plain text in memory...
    if (!password.equals(PASSWORD_ALREADY_SET))
      this.password = Encryption.encodeAsym(
          keyPair.getPublic(),
          password.getBytes());
    return this;
  }

  public LoginSettings setPasswordShouldBeSaved(Boolean flag) {
    prefs.putBoolean(SHOULD_SAVE_ENCRYPTED_PASSWORD, flag);
    // reset the password if we're setting it to false
    if (!flag) prefs.remove(ENCRYPTED_PASSWORD);
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
    byte[] response = prefs.getByteArray(ENCRYPTED_PASSWORD, null);
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
   * Generate a challenge response from a Challenge and the current
   * password.
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
      response = prefs.getByteArray(ENCRYPTED_PASSWORD, new byte[]{});
      if (response.length == 0)
        throw new SessionException(SessionException.Type.Invalid_Credentials);
      else return response;
    }

    if (this.password == null)
      throw new IllegalStateException("Password cannot be null or empty");

    // encrypt the response with the server's public key
    response = Encryption.encodeAsym(
        challenge.getServerKey(),
        Crypt.crypt(
            challenge.getChallenge(keyPair.getPrivate()),
            new String(
                Encryption.decodeAsym(
                    keyPair.getPrivate(),
                    this.password
                )
            )
        ).getBytes()
    );

    // response is encrypted with server's public key, so only that specific
    // server can use it, we can save it, and the saved challenge response
    // on disk cannot be decrypted to find the original password.
    if (shouldPasswordBeSaved())
      prefs.putByteArray(ENCRYPTED_PASSWORD, response);
      // else make sure it is null
    else prefs.remove(ENCRYPTED_PASSWORD);

    return response;
  }
}
