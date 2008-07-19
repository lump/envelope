package us.lump.envelope.client;

import us.lump.envelope.client.ui.MainFrame;

/**
 * Main class.
 *
 * @author troy
 * @version $Id: Main.java,v 1.8 2008/07/19 05:39:44 troy Exp $
 */
public class Main implements Runnable {
  private static Main singleton;

  MainFrame mf = null;

  public static Main getInstance() {
    if (singleton == null) singleton = new Main();
    return singleton;
  }

  private Main() { }

  // for possibly applet starting which are already using a http classloader
  public static void main(String[] args) {
    getInstance().run();
  }

  public void run() {
    mf = MainFrame.getInstance();
  }
}
