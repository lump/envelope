package net.lump.envelope.server;

import net.lump.envelope.shared.entity.User;
import org.apache.log4j.MDC;

/**
 * An attempt at keeping a repository of thread local variables.
 *
 * <p>The request and the command are kept in log4j's MDC rather than in a
 * ThreadLocal of our own, so that the log layouts can print them (as
 * {@code %X{request}} and {@code %X{command}}) on every line logged while the
 * request is on this thread -- Hibernate's and HikariCP's included, and
 * DAO's "began transaction" without DAO having to know what a servlet is.
 * That is what makes a transaction in the log attributable to the request
 * that caused it.
 *
 * @author troy
 */
public class ThreadInfo {

  /** MDC key: the request on this thread, as "METHOD /uri remote-address". */
  public static final String REQUEST = "request";
  /** MDC key: the command being dispatched, as "user commandName". */
  public static final String COMMAND = "command";

  private static ThreadLocal<User> user = new ThreadLocal<User>();

  public static void setUser(User in) {
    user.set(in);
  }

  public static User getUser() {
    return user.get();
  }

  /** Describe the request on this thread for the log; null takes it off. */
  public static void setRequest(String description) {
    put(REQUEST, description);
  }

  /** Describe the command being dispatched on this thread for the log; null takes it off. */
  public static void setCommand(String description) {
    put(COMMAND, description);
  }

  /**
   * Take everything off this thread.  Tomcat reuses its worker threads, so
   * anything left here would be waiting for whichever request lands on this
   * thread next.
   */
  public static void clear() {
    user.set(null);
    MDC.remove(REQUEST);
    MDC.remove(COMMAND);
  }

  private static void put(String key, String value) {
    if (value == null) MDC.remove(key);
    else MDC.put(key, value);
  }
}
