package net.lump.envelope.server;

import net.lump.envelope.shared.entity.User;

/**
 * An attempt at keeping a repository of thread local variables.
 *
 * @author troy
 */
public class ThreadInfo {

  private static ThreadLocal<User> user = new ThreadLocal<User>();

  public static void setUser(User in) {
    user.set(in);
  }

  public static User getUser() {
    return user.get();
  }
}
