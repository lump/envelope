package us.lump.envelope.server;

import us.lump.envelope.entity.User;

/**
 * An attempt at keeping a repository of thread local variables.
 *
 * @author troy
 * @version $Id: ThreadInfo.java,v 1.1 2008/09/13 19:19:30 troy Test $
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
