package us.lump.envelope;

import us.lump.envelope.client.Main;

/**
 * Client bootstrap.
 *
 * @author Troy Bowman
 * @version $Id: Client.java,v 1.3 2007/08/07 01:08:03 troy Exp $
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
        Main.getInstance().run();
      }
    });
  }
}
