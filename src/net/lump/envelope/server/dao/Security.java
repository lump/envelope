package us.lump.envelope.server.dao;

import net.sf.ehcache.Element;
import us.lump.envelope.command.Command;
import us.lump.envelope.command.security.Challenge;
import us.lump.envelope.command.security.Credentials;
import us.lump.envelope.command.security.Crypt;
import us.lump.envelope.entity.User;
import us.lump.envelope.exception.EnvelopeException;
import static us.lump.envelope.exception.EnvelopeException.Name.Invalid_Credentials;
import us.lump.lib.util.Encryption;

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
 * @version $Id: Security.java,v 1.17 2009/04/10 22:49:28 troy Exp $
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
      // update the userCache
      cache.get(USER).put(new Element(username, user));

      // we're authed.
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

