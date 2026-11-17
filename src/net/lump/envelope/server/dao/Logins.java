package net.lump.envelope.server.dao;

import net.lump.envelope.shared.command.security.Scram;
import org.apache.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the server holds about logins in progress: SCRAM exchanges between their
 * two steps, the failure counts that throttle guessing, and the made-up verifier
 * an unknown user is answered with.  In memory, per JVM, like {@link Sessions}.
 *
 * @author troy
 */
final class Logins {

  private static final Logger logger = Logger.getLogger(Logins.class);

  private Logins() {}

  // ---------------------------------------------------------- exchanges

  /** how long the client has between scramStart and scramFinish */
  static final long EXCHANGE_LIFETIME = 2 * 60 * 1000L;
  /** a bound on exchanges held at once, so starting logins cannot exhaust memory */
  static final int MAX_EXCHANGES = 10_000;

  /** One login between its two steps, keyed by the nonce both ends will sign. */
  static final class Exchange {
    final String username;
    final String clientNonce;
    final byte[] salt;
    final int iterations;
    final long started = System.currentTimeMillis();

    Exchange(String username, String clientNonce, byte[] salt, int iterations) {
      this.username = username;
      this.clientNonce = clientNonce;
      this.salt = salt;
      this.iterations = iterations;
    }

    boolean expired(long now) { return now - started > EXCHANGE_LIFETIME; }
  }

  private static final ConcurrentHashMap<String, Exchange> exchanges = new ConcurrentHashMap<String, Exchange>();

  /** @return false when too many logins are in progress to take another */
  static boolean begin(String nonce, Exchange exchange) {
    if (exchanges.size() >= MAX_EXCHANGES) sweep(exchanges, e -> e.expired(System.currentTimeMillis()));
    if (exchanges.size() >= MAX_EXCHANGES) return false;
    exchanges.put(nonce, exchange);
    return true;
  }

  /** The exchange a nonce names, removed: each can be finished exactly once.  Null if unknown or expired. */
  static Exchange finish(String nonce) {
    Exchange e = nonce == null ? null : exchanges.remove(nonce);
    return e == null || e.expired(System.currentTimeMillis()) ? null : e;
  }

  // ---------------------------------------------------------- throttling

  /** failures allowed before each further attempt has to wait */
  static final int FREE_FAILURES = 5;
  /** the longest wait, however many failures */
  static final long MAX_WAIT = 15 * 60 * 1000L;
  /** a record with no failure for this long is forgotten */
  static final long FORGET_AFTER = 60 * 60 * 1000L;
  static final int MAX_RECORDS = 100_000;

  private static final class Record {
    int failures;
    long lastFailure;
    long blockedUntil;
  }

  private static final ConcurrentHashMap<String, Record> records = new ConcurrentHashMap<String, Record>();

  /**
   * How long the caller must still wait before another attempt, in milliseconds,
   * looking at both the user name and the address: zero if it may go ahead.
   */
  static long waitFor(String username, String address) {
    long now = System.currentTimeMillis();
    long wait = 0;
    for (String key : keys(username, address)) {
      Record r = records.get(key);
      if (r != null) synchronized (r) { wait = Math.max(wait, r.blockedUntil - now); }
    }
    return Math.max(0, wait);
  }

  /**
   * Count a failure against the user name and the address.  After the free ones,
   * each failure makes the next attempt wait twice as long as the last, from one
   * second up to {@link #MAX_WAIT}.  Unknown user names are counted the same way,
   * so the throttle says nothing about who exists.
   */
  static void failed(String username, String address) {
    long now = System.currentTimeMillis();
    if (records.size() >= MAX_RECORDS) sweep(records, r -> now - r.lastFailure > FORGET_AFTER);
    for (String key : keys(username, address)) {
      Record r = records.computeIfAbsent(key, k -> new Record());
      synchronized (r) {
        if (now - r.lastFailure > FORGET_AFTER) r.failures = 0;
        r.failures++;
        r.lastFailure = now;
        if (r.failures > FREE_FAILURES) {
          int over = Math.min(r.failures - FREE_FAILURES - 1, 30);
          r.blockedUntil = now + Math.min(1000L << over, MAX_WAIT);
        }
      }
    }
  }

  /** A success clears the user name's record and the address's. */
  static void succeeded(String username, String address) {
    for (String key : keys(username, address)) records.remove(key);
  }

  private static String[] keys(String username, String address) {
    return new String[]{"user:" + (username == null ? "" : username.toLowerCase()),
                        "addr:" + (address == null ? "unknown" : address)};
  }

  // ---------------------------------------------------------- unknown users

  private static volatile byte[] secret;

  /**
   * The secret the made-up verifiers are derived from.  It has to be stable --
   * an unknown name answered with a different salt each time would stand out
   * against a real user's, which never changes -- and it has to be secret, or
   * anyone could compute the made-up salt for a name and compare.  The database
   * password is both, for the life of a deployment, and it is already configured
   * everywhere the server runs; it is only ever hashed here.
   */
  static void setSecretFrom(String databasePassword, String databaseUrl) {
    try {
      secret = MessageDigest.getInstance("SHA-256").digest(
          ("envelope scram unknown-user secret|" + databasePassword + "|" + databaseUrl).getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /** a made-up SCRAM salt for a name no user holds, the same every time it is asked */
  static byte[] fakeSalt(String username) throws GeneralSecurityException {
    byte[] h = Scram.hmac(secret(), ("salt|" + username).getBytes(StandardCharsets.UTF_8));
    byte[] salt = new byte[Scram.SALT_BYTES];
    System.arraycopy(h, 0, salt, 0, salt.length);
    return salt;
  }

  /** a made-up md5-crypt salt in the real ones' shape: eight characters of crypt's alphabet, bare */
  static String fakeMd5Salt(String username) throws GeneralSecurityException {
    final String alphabet = "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    byte[] h = Scram.hmac(secret(), ("md5|" + username).getBytes(StandardCharsets.UTF_8));
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < 8; i++) s.append(alphabet.charAt((h[i] & 0xff) % alphabet.length()));
    return s.toString();
  }

  private static byte[] secret() {
    if (secret == null) throw new IllegalStateException("the unknown-user secret was never set");
    return secret;
  }

  private static <V> void sweep(Map<String, V> map, java.util.function.Predicate<V> stale) {
    int before = map.size();
    for (Iterator<Map.Entry<String, V>> i = map.entrySet().iterator(); i.hasNext(); )
      if (stale.test(i.next().getValue())) i.remove();
    logger.info("swept " + (before - map.size()) + " stale login records");
  }
}
