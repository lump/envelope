package us.lump.envelope.client;

import us.lump.envelope.client.ui.MainFrame;

/**
 * Main class.
 *
 * @author troy
 * @version $Id: Main.java,v 1.7 2008/07/06 04:14:24 troy Exp $
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

  public void setStatus(String status) {
    mf.setStatus(status);
  }
}
