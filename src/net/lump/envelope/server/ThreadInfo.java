package us.lump.envelope.server;

import us.lump.envelope.shared.entity.User;

/**
 * An attempt at keeping a repository of thread local variables.
 *
 * @author troy
 * @version $Id: ThreadInfo.java,v 1.2 2009/07/13 17:21:44 troy Exp $
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
