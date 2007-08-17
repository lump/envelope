package us.lump.envelope.server.dao;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.hibernate.criterion.Restrictions;
import us.lump.envelope.entity.User;
import us.lump.envelope.server.exception.AuthenticationException;
import us.lump.envelope.server.exception.SessionException;
import us.lump.envelope.Command;
import us.lump.envelope.server.security.Challenge;
import us.lump.envelope.server.security.Credentials;
import us.lump.envelope.server.security.Crypt;
import us.lump.lib.util.Encryption;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * DAO dealing with security of the application.
 *
 * @author Troy Bowman
 * @version $Id: Security.java,v 1.3 2007/08/17 18:16:04 troy Exp $
 */
public class Security extends DAO {
  // the server keypair for secure transactions like password encryption
  private static KeyPair serverKeyPair = null;

  // a cache to ask the database for a User less.
  private static final Cache cache;

  static {
    // cache the username in memory for a few seconds or so for those fast
    // queries.
    cache = new Cache("users", 32, false, false, 5, 10);
    CacheManager.getInstance().addCache(cache);
  }

  public Security() {

    // check if keypair is initialized
    if (serverKeyPair == null) {

      // first check to see if we've cached a key in prefs
      Preferences pref = Preferences.userNodeForPackage(Security.class);
      byte[] keyPair =  pref.getByteArray("keyPair", null);
      if (keyPair != null) {
        try {
          serverKeyPair = (KeyPair)(
                  new ObjectInputStream(
                          new ByteArrayInputStream(keyPair))).readObject();
          logger.info("yanked keypair from prefs");
        } catch (IOException e) {
          logger.error("IO Exception on reading of serverKeyPair", e);
          // I don't think we'll have an IO exception with this, ever.
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

        try {
          final ByteArrayOutputStream baos = new ByteArrayOutputStream();
          (new ObjectOutputStream(baos)).writeObject(serverKeyPair);
          pref.putByteArray("keyPair", baos.toByteArray());
        } catch (IOException e) {
          logger.error("IO Exception on saving of serverKeyPair", e);
          // I don't think we'll have an IO exception with this, ever.
        }
      }
    }
  }

  public Boolean authChallengeResponse(String username,
                                       String challengeResponse)
      throws BadPaddingException, NoSuchAlgorithmException, IOException,
      IllegalBlockSizeException, InvalidKeyException, NoSuchPaddingException {
    Boolean authed;

    User user = getUser(username);

    String hash = Encryption.decodeAsym(
            serverKeyPair.getPrivate(),
            challengeResponse);
    if (hash.equals(user.getCryptPassword())) {
      // update the caceh
      cache.put(new Element(username, user));

      // we're authed.
      authed = true;
      logger.info("password auth for \""
              + username
              + "\" successfully verfied");
    } else {
      logger.warn("password auth for \""
              + username
              + "\" FAILED");
      throw new RuntimeException(
              new AuthenticationException("Authentication Failed."));
    }

    return authed;
  }

  public Boolean validateSession(Command c)
      throws NoSuchAlgorithmException, IOException, InvalidKeySpecException,
          SignatureException, InvalidKeyException {
    Credentials credentials = c.getCredentials();

    User user = getUser(credentials.getUsername());
    PublicKey pk = Encryption.decodePublicKey(user.getPublicKey());
    boolean authed = Encryption.verify(pk, String.valueOf(c.hashCode()),
            credentials.getSignature());
    if (authed) {
      logger.debug("signature for \""
              + credentials.getUsername()
              + "\" successfully verfied");
    } else {
      logger.warn("signature for \""
              + credentials.getUsername()
              + "\" FAILED");
      throw new RuntimeException(new SessionException("Invalid Session"));
    }

    return authed;
  }

  public Challenge getChallenge(String username, String publicKey)
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
        Encryption.decodePublicKey(publicKey),
        Crypt.yankSalt(user.getCryptPassword())
    );
  }

  public String getUserPK(String username) {
    return getUser(username).getPublicKey();
  }

  private User getUser(String username) {
    Element ue = cache.get(username);
    User user;
    if (ue != null) {
      user = (User)ue.getValue();
      logger.debug("yanked \"" + username + "\" from ehcache");
    } else {
      List<User> users = list(User.class, Restrictions.eq("name", username));

      if (users.isEmpty()) throw
              new RuntimeException("User " + username + " is invalid.");
      user = users.get(0);
      cache.put(new Element(username, users.get(0)));
    }
    return (user);
  }

}

