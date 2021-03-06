package net.lump.envelope.server.dao;

import net.lump.envelope.shared.command.Command;
import net.lump.envelope.shared.command.security.Challenge;
import net.lump.envelope.shared.command.security.Credentials;
import net.lump.envelope.shared.command.security.Crypt;
import net.lump.envelope.shared.entity.User;
import net.lump.envelope.shared.exception.EnvelopeException;
import static net.lump.envelope.shared.exception.EnvelopeException.Name.Invalid_Credentials;
import net.lump.lib.util.Encryption;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.prefs.Preferences;

/**
 * DAO dealing with security of the application.
 *
 * @author Troy Bowman
 * @version $Id: Security.java,v 1.20 2009/10/02 22:06:23 troy Exp $
 */
public class Security extends DAO {
  // the server keypair for secure transactions like password encryption
  private static KeyPair serverKeyPair = null;

  public Security() {

    // check if keypair is initialized
    if (serverKeyPair == null) {

      // first check to see if we've cached a key in prefs
      Preferences pref = Preferences.userNodeForPackage(Security.class);
      byte[] keyPair = pref.getByteArray("keyPair", null);
      if (keyPair != null) {
        try {
          serverKeyPair = (KeyPair)Encryption.thaw(keyPair);
          logger.info("yanked keypair from prefs");
        } catch (ClassNotFoundException e) {
          logger.warn("couldn't deserialize server keys from prefs", e);
        }
      }

      // if we couldn't yank a key from prefs, let's create a new one
      if (serverKeyPair == null) {
        try {
          serverKeyPair = Encryption.generateKeyPair();
          logger.info("generated a new keypair");
        } catch (NoSuchAlgorithmException e) {
          logger.fatal("I can't generate a keypair!", e);
          System.exit(1);
        }

        pref.putByteArray("keyPair", Encryption.freeze(serverKeyPair));
      }
    }
  }

  public Boolean authedPing() { return ping(); }

  public Boolean ping() { return true; }

  public Boolean authChallengeResponse(String username,
    byte[] challengeResponse)
    throws BadPaddingException, NoSuchAlgorithmException, IOException,
    IllegalBlockSizeException, InvalidKeyException, NoSuchPaddingException {
    Boolean authed;

    User user = getUser(username);

    String hash = new String(
      Encryption.decodeAsym(serverKeyPair.getPrivate(), challengeResponse),
      Encryption.TRANS_ENCODING);

    if (hash.equals(user.getCryptPassword())) {
      authed = true;
      logger.info("password for \"" + username + "\" successfully verfied");
    } else {
      logger.warn("password for \"" + username + "\" FAILED");
      throw new EnvelopeException(Invalid_Credentials);
    }


    return authed;
  }

  public Boolean validateSession(Command c)
    throws NoSuchAlgorithmException, IOException, InvalidKeySpecException,
    SignatureException, InvalidKeyException {
    Credentials credentials = c.getCredentials();

    User user = getUser(credentials.getUsername());

    boolean valid = c.verify(user.getPublicKey());
    if (valid) {
      logger.debug("signature for \""
        + credentials.getUsername()
        + "\" successfully verfied");
    } else {
      logger.error("signature for \""
        + credentials.getUsername()
        + "\" FAILED");
    }

    return valid;
  }

  public Challenge getChallenge(String username, PublicKey publicKey)
    throws NoSuchAlgorithmException, IOException, InvalidKeySpecException,
    BadPaddingException, IllegalBlockSizeException, InvalidKeyException,
    NoSuchPaddingException {
    logger.debug("challenge asked for \"" + username + "\"");

    User user = getUser(username);

    // set the new public key
    user.setPublicKey(publicKey);

    // save it off
    update(user);
    flush();
    commit();

    return new Challenge(
      serverKeyPair.getPublic(),
      publicKey,
      Crypt.yankSalt(user.getCryptPassword())
    );
  }

  //  public CipherInputStream decrypt(InputStream is) throws
  public ByteArrayInputStream decrypt(InputStream is) throws
    IllegalBlockSizeException,
    IOException,
    InvalidKeyException,
    NoSuchAlgorithmException,
    NoSuchPaddingException,
    BadPaddingException {
    return Encryption.decodeAsym(serverKeyPair.getPrivate(), is);
  }

  public Key unwrapSessionKey(byte[] encryptedKey) throws
    IOException,
    InvalidKeyException,
    NoSuchAlgorithmException,
    NoSuchPaddingException {
    return Encryption.unwrapSessionKey(encryptedKey, serverKeyPair.getPrivate());
  }

  public PublicKey getServerPublicKey() {
    return serverKeyPair.getPublic();
  }


}

