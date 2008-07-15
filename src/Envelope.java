import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RMISecurityManager;
import java.rmi.server.RMIClassLoader;

/**
 * The class that starts the client by bootstrapping from RMI.
 *
 * @author Troy Bowman
 * @version $Id: Envelope.java,v 1.5 2008/07/15 02:38:35 troy Exp $
 */

public class Envelope {

  URL codebase;

  private Envelope() throws
      MalformedURLException,
      ClassNotFoundException,
      InstantiationException,
      IllegalAccessException {

    try {
      codebase = new URL("http://" + System.getProperty("codebase") + "/");
//      System.setProperty("java.security.policy",
//                         codebase + "info/security.policy");
//      System.setProperty("java.security.policy",
//                         this.getClass().getResource("security.policy").toString());
      System.setSecurityManager(new RMISecurityManager());

      ClassLoader cl = this.getClass().getClassLoader();
      String className = "us.lump.envelope.Client";
//      Class clientClass = cl.loadClass(className);
      Class clientClass = RMIClassLoader.loadClass(codebase, className);
      ((Runnable)clientClass.newInstance()).run();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private String join(String delimiter, Object[] array) {
    String out = "";
    int count = 0;
    for (Object o : array) {
      if (count > 0) out += delimiter;
      out += o.toString();
    }
    return out;
  }

  /**
   * main method.
   *
   * @param args command line args
   */
  public static void main(String args[]) {
    try {
      new Envelope();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
