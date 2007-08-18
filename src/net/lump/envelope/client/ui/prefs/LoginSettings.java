package us.lump.envelope.client.ui.prefs;

import us.lump.envelope.server.security.Challenge;
import us.lump.envelope.server.security.Crypt;
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

/**
 * Singleton for keeping track of login information.  (Basically the
 * username and password.
 *
 * @author Troy Bowman
 * @version $Id: LoginSettings.java,v 1.4 2007/08/18 04:49:44 troy Exp $
 */
public class LoginSettings {

  // these strings define preference keys.
  private static final String USER = "username";
  private static final String SHOULD_SAVE_ENCRYPTED_PASSWORD = "saveEncrypedPassword?";
  private static final String ENCRYPTED_PASSWORD = "encryptedPassword";

  // preferences reference, defined at instantiation of the singleton.
  private Preferences prefs;

  // the key-pair, generated once at instantiation of the singleton.
  private KeyPair keyPair;

  // supress unused because there is no getter for password.
  @SuppressWarnings({"UnusedDeclaration"})
  transient private byte[] password;

  // the singleton
  private static LoginSettings singleton;

  private LoginSettings() {
    prefs = Preferences.userNodeForPackage(this.getClass());
    try {
      keyPair = Encryption.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  public static LoginSettings getInstance() {
    if (singleton == null) singleton = new LoginSettings();
    return singleton;
  }

  public String getUsername() {
    return prefs.get(USER, null);
  }

  public KeyPair getKeyPair() {
    return keyPair;
  }

  public Boolean shouldPasswordBeSaved() {
    return prefs.getBoolean(SHOULD_SAVE_ENCRYPTED_PASSWORD, Boolean.FALSE);
  }

  public LoginSettings setPassword(String password)
          throws BadPaddingException, NoSuchAlgorithmException,
          IllegalBlockSizeException, InvalidKeyException,
          NoSuchPaddingException {
    // keep the password from being plain text in memory...
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
   * @return a byte array which comprises the encrypted response.
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
   * Get the generate a challenge response from a Challenge and the current
   * password.
   *
   * @param challenge the Challenge object from the server.
   * @return a byte array which comprises the encrypted response.
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

    if (this.password == null)
      throw new IllegalStateException("Password cannot be null or empty");

    // encrypt the response with the server's public key
    byte[] response = Encryption.encodeAsym(
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

    // save the encrypted password to prefs if the user prefers to save it
    if (shouldPasswordBeSaved())
      prefs.putByteArray(ENCRYPTED_PASSWORD, response);
      // else make sure it is null
    else prefs.remove(ENCRYPTED_PASSWORD);

    return response;
  }
}
