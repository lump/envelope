import javax.swing.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.RMISecurityManager;
import java.rmi.server.RMIClassLoader;

/**
 * The class that starts the client by bootstrapping from RMI.
 *
 * @author Troy Bowman
 * @version $Id: Envelope.java,v 1.9 2008/09/12 04:04:24 troy Exp $
 */

public class Envelope {

  private static String className = "us.lump.envelope.Client";

  private Envelope() throws
      MalformedURLException,
      ClassNotFoundException,
      InstantiationException,
      IllegalAccessException {

    boolean found = false;
    while (!found) {
      try {
        HttpURLConnection conn = (HttpURLConnection)
            (new URL(urlCodebase()
                     + className.replaceAll("\\.", "/")
                     + ".class"))
                .openConnection();
        conn.setRequestMethod("HEAD");
        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) found = true;
        else JOptionPane.showMessageDialog(null,
                                           "Invalid Codebase: "
                                           + conn.getResponseMessage());
      }
      catch (Exception e) {
        JOptionPane.showMessageDialog(null,
                                      "Invalid Codebase: " + e.getMessage());
        found = false;
      }
      if (!found) {
        String cb = JOptionPane.showInputDialog("Please specify the codebase:",
                                                System.getProperty("codebase"));
        if (cb == null) System.exit(1);
        System.setProperty("codebase", cb);
      }
    }
    System.setProperty("java.security.policy",
                       urlCodebase() + "info/security.policy");
//      System.setProperty("java.security.policy",
//                         this.getClass().getResource("security.policy").toString());
//    System.setSecurityManager(new RMISecurityManager());
//      System.setProperty("java.class.path",
//                         System.getProperty("java.class.path") + ":" + codebase);

    // try to load the class normally, else use RMI classloader
    try {
      Class clientClass = Class.forName(
          className, true, Thread.currentThread().getContextClassLoader());
      System.err.println("loading from local classloader");
      ((Runnable)clientClass.newInstance()).run();
    }
    catch (ClassNotFoundException e) {
      System.err.println("loading from server");
      Class clientClass = Class.forName(
          className, true,
          RMIClassLoader.getClassLoader(urlCodebase().toString()));
//      Class clientClass = Class.forName(
//          className, true,
//          new URLClassLoader(new URL[]{urlCodebase()},
//                             Thread.currentThread().getContextClassLoader()));

      ((Runnable)clientClass.newInstance()).run();
    }
  }

  private URL urlCodebase() throws MalformedURLException {
    return new URL("http://" + System.getProperty("codebase") + "/");
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
