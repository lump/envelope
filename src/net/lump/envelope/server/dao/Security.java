package net.lump.envelope.server.dao;

import net.lump.envelope.shared.command.Command;
import net.lump.envelope.shared.command.security.Challenge;
import net.lump.envelope.shared.command.security.Crypt;
import net.lump.envelope.shared.entity.User;
import net.lump.envelope.shared.exception.EnvelopeException;
import static net.lump.envelope.shared.exception.EnvelopeException.Name.Invalid_Credentials;
import net.lump.lib.util.Day;
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

  /**
   * The readiness probe's question (/info/ready dispatches this through
   * Controller like any other command).  Constructing this DAO already opened a
   * session and began a transaction, so reaching here means the pool handed out
   * a connection.  The query proves the schema: the swarm's config project
   * bootstraps <em>empty</em> databases, so "pointed at the wrong database" is
   * a real way to be up with every login failing.
   *
   * <p>It asks for the latest transaction's day, and asks twice: once through
   * the entity mapping, which is the {@code java.sql.Date} read every client
   * query makes, and once as the text the database itself spells the column in,
   * where no instant and so no zone is involved.  The two have to agree.  That
   * turns the probe into a standing test of the date handling this application
   * has lost days to before (see {@link Day}): a server given a zone east of
   * Greenwich, a driver that starts applying one, a stray
   * {@code hibernate.jdbc.time_zone}, and the container goes unhealthy saying
   * which day became which, rather than every user quietly seeing -- and the
   * form quietly writing back -- the day before.
   *
   * <p>It used to count the users table, which on a fifteen-second cadence read
   * in the log like someone probing accounts, besides telling any
   * unauthenticated caller how many there were.  The latest transaction's day
   * is a sign of life instead of a census.
   */
  public String ready() {
    java.util.Date read = (java.util.Date)getCurrentSession()
        .createQuery("select max(t.date) from Transaction t").uniqueResult();
    String stored = (String)getCurrentSession()
        .createNativeQuery("select cast(max(date) as char) from transactions").uniqueResult();
    if (read == null && stored == null) return "ready: no transactions yet";
    String day = Day.toIso(read);
    if (!java.util.Objects.equals(stored, day))
      throw new IllegalStateException("the latest transaction is stored on " + stored
          + " but read back as " + day + " -- a day is being read in the wrong zone");
    return "ready: latest transaction " + day;
  }

  public byte[] authChallengeResponse(String username,
    byte[] challengeResponse, PublicKey publicKey)
    throws BadPaddingException, NoSuchAlgorithmException, IOException,
    IllegalBlockSizeException, InvalidKeyException, NoSuchPaddingException {

    User user = getUser(username);

    String hash = new String(
      Encryption.decodeAsym(serverKeyPair.getPrivate(), challengeResponse),
      Encryption.TRANS_ENCODING);

    if (!hash.equals(user.getCryptPassword())) {
      logger.warn("password for \"" + username + "\" FAILED");
      throw new EnvelopeException(Invalid_Credentials);
    }

    logger.info("password for \"" + username + "\" successfully verfied");

    // The credential is proven, so this key can be trusted -- but only for as
    // long as this login lasts, so it is bound to the session instead of being
    // written to the user row.  Nothing in the database is an authenticator.
    String sessionId = Sessions.open(username, publicKey);

    // Hand the session id back encrypted to the key that was just proven, so
    // only the client that actually authenticated can read it.
    return Encryption.encodeAsym(publicKey, sessionId.getBytes(Encryption.TRANS_ENCODING));
  }

  /**
   * Authenticate one command from the material carried alongside it.
   *
   * <p>Static, and deliberately touches no database: a DAO instance would open a
   * transaction, and more importantly nothing persisted is trusted here.  The
   * signature is checked against the key bound to the session when the password
   * was proven.
   *
   * <p>Three things must hold, and each closes a different hole: the session must
   * be live, the timestamp must be close to ours (so a captured signature is
   * useful only briefly), and the signature must be unspent (so it is not useful
   * even twice).  The signature itself covers a digest of the exact command
   * bytes, so it cannot be lifted onto a different command.
   *
   * @param sessionId  session identifier offered by the caller
   * @param stampValue client timestamp, as sent
   * @param signature  signature over {@link Command#signaturePayload}
   * @param digest     digest of the serialized command the caller sent
   *
   * @return the authenticated Session, or null if it does not check out
   */
  public static Sessions.Session validateSession(
      String sessionId, String stampValue, String signature, String digest) {

    Sessions.Session session = Sessions.get(sessionId);
    if (session == null) {
      logger.warn("no live session for the offered session id");
      return null;
    }

    long stamp;
    try {
      stamp = Long.parseLong(stampValue);
    } catch (NumberFormatException e) {
      logger.warn("unparseable timestamp from \"" + session.getUsername() + "\"");
      return null;
    }

    if (!Sessions.stampIsFresh(stamp)) {
      logger.warn("timestamp out of window for \"" + session.getUsername() + "\"");
      return null;
    }

    try {
      if (!Encryption.verify(session.getPublicKey(),
                             Command.signaturePayload(sessionId, stamp, digest),
                             signature)) {
        logger.warn("signature for \"" + session.getUsername() + "\" FAILED");
        return null;
      }
    } catch (GeneralSecurityException e) {
      logger.warn("could not verify signature for \"" + session.getUsername() + "\"", e);
      return null;
    } catch (IOException e) {
      logger.warn("could not verify signature for \"" + session.getUsername() + "\"", e);
      return null;
    }

    // a signature is good exactly once
    if (!Sessions.spend(signature)) {
      logger.warn("replayed signature for \"" + session.getUsername() + "\"");
      return null;
    }

    return session;
  }

  public Challenge getChallenge(String username, PublicKey publicKey)
    throws NoSuchAlgorithmException, IOException, InvalidKeySpecException,
    BadPaddingException, IllegalBlockSizeException, InvalidKeyException,
    NoSuchPaddingException {
    logger.debug("challenge asked for \"" + username + "\"");

    User user = getUser(username);

    // The caller-supplied public key is deliberately NOT persisted here.
    // getChallenge is session-not-required, so anyone may call it; writing the
    // key before the password has been proven let an unauthenticated caller
    // overwrite the very key validateSession() trusts.  The key travels on to
    // authChallengeResponse() and is stored only once the credential checks out.

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

