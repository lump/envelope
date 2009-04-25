package us.lump.envelope.client;

import com.incors.plaf.alloy.themes.bedouin.BedouinTheme;
import org.apache.log4j.BasicConfigurator;
import us.lump.envelope.client.ui.MainFrame;
import us.lump.envelope.client.ui.defs.Strings;

import javax.swing.*;

/**
 * Main class.
 *
 * @author troy
 * @version $Id: Main.java,v 1.20 2009/04/25 03:33:55 troy Exp $
 */
public class Main implements Runnable {
  private static Main singleton;

  MainFrame mf = null;

  public static Main getInstance() {
    if (singleton == null) singleton = new Main();
    return singleton;
  }

  private Main() {

    if (System.getProperty("mrj.version") != null) {
      System.setProperty("com.apple.macos.useScreenMenuBar", "true");
      System.setProperty("apple.laf.useScreenMenuBar", "true");
      System.setProperty("com.apple.mrj.application.apple.menu.about.name", Strings.get("envelope.budget"));
      // don't fuss with LAF
      return;
    }


//    try {
////       try nimbus first, since it's the coolest
//      UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
//    }
//    catch (Exception e) {
    try {
      // try alloy next, since it is clean and nice
      // borrow the jetbrains license for now, until we get serious
      com.incors.plaf.alloy.AlloyLookAndFeel.setProperty("alloy.licenseCode", "4#JetBrains#1ou2uex#6920nk");
      javax.swing.LookAndFeel alloyLnF = new com.incors.plaf.alloy.AlloyLookAndFeel(new BedouinTheme());
      alloyLnF.initialize();
      javax.swing.UIManager.setLookAndFeel(alloyLnF);
    } catch (Exception ex) {
      try {
        // oh well, let's just use ye olde metal
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
      } catch (Exception e1) {
        // nevermind
      }
    }
//    }
  }

  // for possibly applet starting which are already using a http classloader
  public static void main(String[] args) {
    BasicConfigurator.configure();
    getInstance().run();
  }

  public void run() {
    mf = MainFrame.getInstance();
  }
}