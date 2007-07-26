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
import java.util.prefs.Preferences;

/**
 * .
 *
 * @author Troy Bowman
 * @version $Id: LoginSettings.java,v 1.1 2007/07/26 06:52:06 troy Exp $
 */
public class LoginSettings {

  // these strings define preference keys.
  private static final String USER = "username";
  private static final String ENCRYPTED_PASSWORD_SAVED = "saveEncrypedPassword?";
  private static final String ENCRYPTED_PASSWORD = "encryptedPassword";

  // preferences reference, defined at instantiation of the singleton.
  private Preferences prefs;

  // the key-pair, generated once at instantiation of the singleton.
  private KeyPair keyPair;

  // supress unused because there is no getter for password.
  @SuppressWarnings({"UnusedDeclaration"})
  private String password;

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

  public LoginSettings getInstance() {
    if (singleton == null) singleton = new LoginSettings();
    return singleton;
  }

  public String getUsername() {
    return prefs.get(USER, null);
  }

  public KeyPair getKeyPair() {
    return keyPair;
  }

  public Boolean isPasswordSaved() {
    return Boolean.valueOf(prefs.get(ENCRYPTED_PASSWORD_SAVED, Boolean.FALSE.toString()));
  }

  public LoginSettings setPassword(String password)
          throws BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException,
          InvalidKeyException, NoSuchPaddingException {
    // keep the password from being plain text in memory...
    this.password = Encryption.encodeAsym(keyPair.getPublic(), password);
    return this;
  }

  public LoginSettings setPasswordSaved(Boolean flag) {
    prefs.put(ENCRYPTED_PASSWORD_SAVED, flag.toString());
    // reset the password if we're setting it to false
    if (!flag) prefs.put(ENCRYPTED_PASSWORD, "");
    return this;
  }

  public LoginSettings setUsername(String username) {
    prefs.put(USER, username);
    return this;
  }

  public String challengeResponse() {
    String response = prefs.get(ENCRYPTED_PASSWORD, null);
    if (!isPasswordSaved() || null == response || response.equals(""))
      throw new IllegalStateException("Password is not saved.");
    else
      return response;
  }

  public String challengeResponse(Challenge challenge, String password)
          throws NoSuchAlgorithmException, BadPaddingException, IOException, IllegalBlockSizeException,
          InvalidKeyException, NoSuchPaddingException {
    setPassword(password);
    return challengeResponse(challenge);
  }

  public String challengeResponse(Challenge challenge)
          throws BadPaddingException, NoSuchAlgorithmException,
          IllegalBlockSizeException, InvalidKeyException,
          NoSuchPaddingException, IOException {

    if (this.password == null) throw new IllegalStateException("Password cannot be null");

    // encrypt the response with the server's key
    String response = Encryption.encodeAsym(
            challenge.getServerKey(),
            Crypt.crypt(
                    Encryption.decodeAsym(keyPair.getPrivate(), challenge.getChallenge()),
                    Encryption.decodeAsym(keyPair.getPrivate(), this.password)));

    // save the encrypted password to prefs if the user prefers to save it
    if (isPasswordSaved()) prefs.put(ENCRYPTED_PASSWORD, response);
    else prefs.put(ENCRYPTED_PASSWORD, null);

    return response;
  }
}
