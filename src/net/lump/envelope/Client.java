package us.lump.envelope;

import us.lump.envelope.client.Main;

/**
 * Client bootstrap.
 *
 * @author Troy Bowman
 * @version $Id: Client.java,v 1.5 2008/07/15 23:13:26 troy Test $
 */
@SuppressWarnings({"UnusedDeclaration"})
public class Client implements Runnable {

  /**
   * Bootstrap the Main class from a class that is only loaded from the server.
   * This is necessary so that the RMIClassloader can be in effect for all
   * classes loaded on the Client.
   */
  @SuppressWarnings("unchecked")
  public void run() {
    Main.getInstance().run();
  }
}
