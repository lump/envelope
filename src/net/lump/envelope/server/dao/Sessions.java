package net.lump.envelope.server.dao;

import net.lump.lib.util.Encryption;
import org.apache.log4j.Logger;

import java.security.PublicKey;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side sessions.
 *
 * <p>A session is minted only by {@link Security#authChallengeResponse}, once the
 * password has actually been proven, and it remembers the public key that was
 * proven alongside it.  Commands are then authenticated by verifying their
 * signature against <em>that</em> key rather than against anything in the
 * database, which is what keeps a writable user row out of the auth path.
 *
 * <p>Because the client's keypair is regenerated on every launch, its lifetime
 * already matches a session's -- so binding the two makes the session id
 * sender-constrained: capturing it is not enough to use it.
 *
 * <p>State is per-JVM and in memory.  One Tomcat, so that is fine; a second node
 * would need sticky sessions or a shared store.
 *
 * @author Troy Bowman
 */
public final class Sessions {

  private static final Logger logger = Logger.getLogger(Sessions.class);

  /** how long a session may sit unused before it is dropped */
  public static final long IDLE_TIMEOUT = 30 * 60 * 1000L;
  /** how far a command's timestamp may drift from our clock and still be accepted */
  public static final long STAMP_WINDOW = 5 * 60 * 1000L;
  /** sweep the replay cache once it grows past this */
  private static final int SWEEP_THRESHOLD = 4096;

  private static final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<String, Session>();

  /**
   * Signatures already spent.  A signature is bound to one command and one
   * timestamp, and a timestamp outside STAMP_WINDOW is refused anyway, so this
   * only ever has to remember one window's worth.
   */
  private static final ConcurrentHashMap<String, Long> spent = new ConcurrentHashMap<String, Long>();

  private Sessions() { }

  /** What the server knows about a logged-in client. */
  public static final class Session {
    private final String username;
    private final PublicKey publicKey;
    private volatile long lastSeen;

    private Session(String username, PublicKey publicKey) {
      this.username = username;
      this.publicKey = publicKey;
      this.lastSeen = System.currentTimeMillis();
    }

    public String getUsername() { return username; }

    public PublicKey getPublicKey() { return publicKey; }

    boolean isExpired(long now) { return (now - lastSeen) > IDLE_TIMEOUT; }
  }

  /**
   * Open a session for a user whose credentials have just been verified.
   *
   * @param username  the authenticated user
   * @param publicKey the key proven in the same handshake, used to verify commands
   *
   * @return String the session identifier
   */
  public static String open(String username, PublicKey publicKey) {
    String id = Encryption.randomToken();
    sessions.put(id, new Session(username, publicKey));
    sweep();
    logger.info("opened session for \"" + username + "\"");
    return id;
  }

  /**
   * Look up a live session, refreshing its idle clock.
   *
   * @param id the session identifier
   *
   * @return Session or null if unknown or expired
   */
  public static Session get(String id) {
    if (id == null) return null;
    Session s = sessions.get(id);
    if (s == null) return null;
    long now = System.currentTimeMillis();
    if (s.isExpired(now)) {
      sessions.remove(id);
      logger.info("session for \"" + s.getUsername() + "\" expired");
      return null;
    }
    s.lastSeen = now;
    return s;
  }

  /** Drop a session. */
  public static void close(String id) {
    if (id != null) sessions.remove(id);
  }

  /**
   * Spend a signature.  A signature is good exactly once, so a captured request
   * cannot be replayed even inside the timestamp window.
   *
   * @param signature the signature offered
   *
   * @return boolean true if this is the first time we have seen it
   */
  public static boolean spend(String signature) {
    if (spent.size() > SWEEP_THRESHOLD) sweep();
    return spent.putIfAbsent(signature, System.currentTimeMillis()) == null;
  }

  /**
   * Is the client's timestamp close enough to ours to be plausible? Bounds how
   * long a captured signature could be useful, and bounds the replay cache.
   *
   * @param stamp client-supplied epoch millis
   *
   * @return boolean
   */
  public static boolean stampIsFresh(long stamp) {
    return Math.abs(System.currentTimeMillis() - stamp) <= STAMP_WINDOW;
  }

  private static void sweep() {
    long now = System.currentTimeMillis();
    for (Iterator<Map.Entry<String, Long>> i = spent.entrySet().iterator(); i.hasNext(); )
      if ((now - i.next().getValue()) > STAMP_WINDOW) i.remove();
    for (Iterator<Map.Entry<String, Session>> i = sessions.entrySet().iterator(); i.hasNext(); )
      if (i.next().getValue().isExpired(now)) i.remove();
  }
}
