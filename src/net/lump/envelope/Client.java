package us.lump.envelope;

import us.lump.envelope.client.Main;

/**
 * Client bootstrap.
 *
 * @author Troy Bowman
 * @version $Id: Client.java,v 1.2 2007/07/26 06:52:06 troy Exp $
 */
@SuppressWarnings({"UnusedDeclaration"})
public class Client implements Runnable {

  /**
   * Bootstrap the Main class from a class that is only loaded from the server.
   * This is necessary so that the RMIClassloader can be in effect for all classes loaded on the Client.
   */
  @SuppressWarnings("unchecked")
  public void run() {
    javax.swing.SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        new Main().run();
      }
    });
  }
}
