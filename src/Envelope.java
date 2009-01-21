import us.lump.lib.util.ChildFirstClassLoader;

import javax.swing.*;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * The class that starts the client by bootstrapping from RMI.
 *
 * @author Troy Bowman
 * @version $Id: Envelope.java,v 1.12 2009/01/21 06:52:29 troy Test $
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
//    try {
//      Class clientClass = Class.forName(
//          className, true, Thread.currentThread().getContextClassLoader());
//      System.err.println("loading from local classloader");
//      ((Runnable)clientClass.newInstance()).run();
//    }
//    catch (ClassNotFoundException e) {
      System.err.println("loading from server");
//      Class clientClass = Class.forName(
//          className, true,
//          RMIClassLoader.getClassLoader(urlCodebase().toString()));
    Class clientClass = Class.forName(
          className, true,
          new ChildFirstClassLoader(
              urls(),
              Thread.currentThread().getContextClassLoader()));

      ((Runnable)clientClass.newInstance()).run();
//    }
  }

  private URL urlCodebase() throws MalformedURLException {
    return new URL("http://" + System.getProperty("codebase") + "/");
  }

  private URL[] urls() throws MalformedURLException {
    ArrayList<URL> urls = new ArrayList<URL>();
    StringTokenizer st =
        new StringTokenizer(System.getProperty("java.class.path", ""),
                            File.pathSeparator);

    while (st.hasMoreTokens()) {
      String token = st.nextToken();
      if (!token.equals("")) {
        File f = new File(token);
        if (f.exists()) urls.add(f.toURI().toURL());
      }
    }
    urls.add(urlCodebase());
    return urls.toArray(new URL[]{});
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
