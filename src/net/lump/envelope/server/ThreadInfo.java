package net.lump.envelope.server;

import net.lump.envelope.shared.entity.User;

/**
 * An attempt at keeping a repository of thread local variables.
 *
 * @author troy
 * @version $Id: ThreadInfo.java,v 1.3 2009/10/02 22:06:23 troy Exp $
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
