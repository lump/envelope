package us.lump.envelope.server.dao;

import net.sf.ehcache.Element;
import us.lump.envelope.Command;
import us.lump.envelope.entity.User;
import us.lump.envelope.exception.SessionException;
import us.lump.envelope.exception.DataException;
import us.lump.envelope.server.security.Challenge;
import us.lump.envelope.server.security.Credentials;
import us.lump.envelope.server.security.Crypt;
import us.lump.lib.util.Encryption;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.prefs.Preferences;

/**
 * DAO dealing with security of the application.
 *
 * @author Troy Bowman
 * @version $Id: Security.java,v 1.13 2008/08/31 00:29:59 troy Exp $
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

    User user = null;
    try {
      user = getUser(username);
    } catch (DataException e) {
      throw new SessionException(SessionException.Type.Invalid_User, e);
    }

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
      throw new SessionException(SessionException.Type.Invalid_Credentials);
    }

    return authed;
  }

  public Boolean validateSession(Command c)
      throws NoSuchAlgorithmException, IOException, InvalidKeySpecException,
      SignatureException, InvalidKeyException {
    Credentials credentials = c.getCredentials();

    User user = null;
    try {
      user = getUser(credentials.getUsername());
    } catch (DataException e) {
      throw new SessionException(SessionException.Type.Invalid_User, e);
    }

    boolean authed = c.verify(user.getPublicKey());
    if (authed) {
      logger.debug("signature for \""
                   + credentials.getUsername()
                   + "\" successfully verfied");
    } else {
      logger.error("signature for \""
                   + credentials.getUsername()
                   + "\" FAILED");
      throw new RuntimeException(new SessionException(
          SessionException.Type.Invalid_Session));
    }

    return authed;
  }

  public Challenge getChallenge(String username, PublicKey publicKey)
      throws NoSuchAlgorithmException, IOException, InvalidKeySpecException,
      BadPaddingException, IllegalBlockSizeException, InvalidKeyException,
      NoSuchPaddingException {
    logger.debug("challenge asked for \"" + username + "\"");
    User user = null;
    try {
      user = getUser(username);
    } catch (DataException e) {
      throw new SessionException(SessionException.Type.Invalid_User, e);
    }
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

  public ByteArrayInputStream decrypt(InputStream is) throws
      IllegalBlockSizeException,
      IOException,
      InvalidKeyException,
      NoSuchAlgorithmException,
      NoSuchPaddingException,
      BadPaddingException {
    return Encryption.decodeAsym(serverKeyPair.getPrivate(), is);
  }

  public PublicKey getServerPublicKey() {
    return serverKeyPair.getPublic();
  }


}

