package net.lump.envelope.server.dao;

import net.lump.envelope.shared.command.Command;
import net.lump.envelope.server.ThreadInfo;
import net.lump.envelope.shared.command.security.Scram;
import net.lump.envelope.shared.command.security.ScramChallenge;
import net.lump.envelope.shared.command.security.ScramResult;
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
    java.util.Date read = getCurrentSession()
        .createSelectionQuery("select max(t.date) from Transaction t", java.util.Date.class).uniqueResult();
    String stored = getCurrentSession()
        .createNativeQuery("select cast(max(date) as char) from transactions", String.class).uniqueResult();
    if (read == null && stored == null) return "ready: no transactions yet";
    String day = Day.toIso(read);
    if (!java.util.Objects.equals(stored, day))
      throw new IllegalStateException("the latest transaction is stored on " + stored
          + " but read back as " + day + " -- a day is being read in the wrong zone");
    return "ready: latest transaction " + day;
  }

  // ------------------------------------------------------------------------
  // Login: SCRAM-SHA-256 (see Scram).  Two session-less commands.
  //
  // Until 0.14 the login sent the stored md5-crypt hash, encrypted to the server
  // key: the same bytes every time, and the same value the users table held, so
  // a captured login or a copied table logged anyone in.  Now the client proves
  // it knows the password with a value good for this one exchange, the server
  // stores only a verifier that makes no proof, and the server proves itself
  // back.  Every failure -- unknown user, wrong password, stale or replayed
  // exchange -- is the same Invalid_Credentials, and an unknown user is answered
  // with a made-up verifier as stable as a real one, so nothing here says who
  // exists.  Failures are throttled by user name and by address (Logins).
  // ------------------------------------------------------------------------

  private static final String REFUSED = "the user name or password is not right";

  /** A user whose verifier is usable, or null for none -- never an exception that names the difference. */
  private User verifiedUser(String username) {
    if (username == null || username.isEmpty()) return null;
    User user = getCurrentSession()
        .createSelectionQuery("from User u where u.name = :name", User.class)
        .setParameter("name", username).uniqueResult();
    if (user == null || user.getScramStoredKey() == null || user.getScramServerKey() == null
        || user.getScramSalt() == null || user.getScramIterations() == null || user.getMd5Salt() == null) return null;
    return user;
  }

  private static void refuseIfThrottled(String username) throws EnvelopeException {
    long wait = Logins.waitFor(username, ThreadInfo.getRemoteAddress());
    if (wait > 0) {
      long seconds = Math.max(1, (wait + 999) / 1000);
      throw new EnvelopeException(Invalid_Credentials,
          "too many failed logins; try again in " + seconds + (seconds == 1 ? " second" : " seconds"));
    }
  }

  /**
   * Step one: name the user and a fresh client nonce; get back what the proof is
   * computed from.  An unknown user is answered as if known.
   */
  public ScramChallenge scramStart(String username, String clientNonce) throws EnvelopeException {
    refuseIfThrottled(username);
    if (clientNonce == null || clientNonce.length() < 16 || clientNonce.length() > 128)
      throw new EnvelopeException(Invalid_Credentials, REFUSED);
    try {
      User user = verifiedUser(username);
      String md5Salt = user != null ? user.getMd5Salt() : Logins.fakeMd5Salt(username);
      byte[] salt = user != null ? user.getScramSalt() : Logins.fakeSalt(username);
      int iterations = user != null ? user.getScramIterations() : Scram.ITERATIONS;

      String nonce = clientNonce + Scram.newNonce();
      if (!Logins.begin(nonce, new Logins.Exchange(username, clientNonce, salt, iterations)))
        throw new EnvelopeException(Invalid_Credentials, "the server is busy; try again shortly");
      return new ScramChallenge(nonce, md5Salt, salt, iterations, serverKeyPair.getPublic());
    } catch (GeneralSecurityException e) {
      throw new EnvelopeException(Invalid_Credentials, REFUSED);
    }
  }

  /**
   * Step two: the proof for that exchange, and the client key the session is to
   * be bound to (the proof covers it).  A good proof opens a session and returns
   * the server's signature with the session id encrypted to that key.
   */
  public ScramResult scramFinish(String nonce, byte[] proof, PublicKey publicKey) throws EnvelopeException {
    Logins.Exchange exchange = Logins.finish(nonce);
    String address = ThreadInfo.getRemoteAddress();
    if (exchange == null) throw new EnvelopeException(Invalid_Credentials, REFUSED);
    refuseIfThrottled(exchange.username);
    try {
      User user = verifiedUser(exchange.username);
      byte[] am = publicKey == null ? null
          : Scram.authMessage(exchange.username, exchange.clientNonce, nonce, exchange.salt, exchange.iterations, publicKey);
      if (user == null || am == null || !Scram.proofIsValid(user.getScramStoredKey(), am, proof)) {
        Logins.failed(exchange.username, address);
        logger.warn("login for \"" + exchange.username + "\" FAILED");
        throw new EnvelopeException(Invalid_Credentials, REFUSED);
      }
      Logins.succeeded(exchange.username, address);
      logger.info("login for \"" + exchange.username + "\" verified");

      // the proof covered this key, so it is the one the session is bound to
      String sessionId = Sessions.open(exchange.username, publicKey);
      return new ScramResult(Scram.serverSignature(user.getScramServerKey(), am),
          Encryption.encodeAsym(publicKey, sessionId.getBytes(Encryption.TRANS_ENCODING)));
    } catch (GeneralSecurityException | java.io.UnsupportedEncodingException e) {
      throw new EnvelopeException(Invalid_Credentials, REFUSED);
    }
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

