package us.lump.envelope.server.dao;

import us.lump.envelope.entity.User;

/**
 * An attempt at keeping a repository of thread local variables.
 *
 * @author troy
 * @version $Id: ThreadInfo.java,v 1.1 2007/09/09 07:17:55 troy Exp $
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
