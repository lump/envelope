package us.lump.envelope.client;

import us.lump.envelope.client.ui.MainFrame;

import javax.swing.*;

/**
 * Main class.
 *
 * @author troy
 * @version $Id: Main.java,v 1.9 2008/11/05 00:48:25 troy Exp $
 */
public class Main implements Runnable {
  private static Main singleton;

  MainFrame mf = null;

  public static Main getInstance() {
    if (singleton == null) singleton = new Main();
    return singleton;
  }

  private Main() {
    try {
//          UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");

      // borrow the jetbrains license for now, until we get serious
      com.incors.plaf.alloy.AlloyLookAndFeel
          .setProperty("alloy.licenseCode", "4#JetBrains#1ou2uex#6920nk");
      javax.swing.LookAndFeel alloyLnF =
          new com.incors.plaf.alloy.AlloyLookAndFeel();
      alloyLnF.initialize();
      javax.swing.UIManager.setLookAndFeel(alloyLnF);
    }
    catch (Exception e) {
      try {
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
      }
      catch (Exception ex) { }
    }
  }

  // for possibly applet starting which are already using a http classloader
  public static void main(String[] args) {
    getInstance().run();
  }

  public void run() {
    mf = MainFrame.getInstance();
  }
}
