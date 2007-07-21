package us.lump.envelope.client;

import com.apple.eawt.Application;
import com.apple.eawt.ApplicationEvent;
import us.lump.envelope.client.ui.AboutBox;
import us.lump.envelope.client.ui.MainContent;
import us.lump.envelope.client.ui.Preferences;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.client.portal.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

/**
 * .
 *
 * @author troy
 * @version $Id: Main.java,v 1.1 2007/07/21 20:15:04 troy Exp $
 */
public class Main implements Runnable {
  private JFrame frame;
  private AboutBox aboutBox;
  private Preferences appPrefs;
  private java.util.prefs.Preferences prefs;
  private MainContent mainContent = new MainContent();
  static final JMenuBar mainMenuBar = new JMenuBar();

  {
    prefs = java.util.prefs.Preferences.userNodeForPackage(this.getClass());
  }

  public void run() {

    frame = new JFrame(Strings.get("envelope_budget"));
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setJMenuBar(mainMenuBar);
    frame.add(mainContent,BorderLayout.CENTER);

    mainContent.setVisible(true);

    int shortcutKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    if (System.getProperty("os.name").toLowerCase().matches("^.*?mac os x.*$")) {
      // the Mac specific code here
      System.getProperties().put("apple.laf.useScreenMenuBar", true);

      Application fApplication = Application.getApplication();
      fApplication.setEnabledPreferencesMenu(true);
      fApplication.addApplicationListener(new com.apple.eawt.ApplicationAdapter() {
        public void handleAbout(ApplicationEvent e) {
          aboutBox();
          e.setHandled(true);
        }

        public void handleOpenApplication(ApplicationEvent e) {
        }

        public void handleOpenFile(ApplicationEvent e) {
        }

        public void handlePreferences(ApplicationEvent e) {
          appPrefs.setVisible(true);
        }

        public void handlePrintFile(ApplicationEvent e) {
        }

        public void handleQuit(ApplicationEvent e) {
          System.exit(0);
        }
      });
    } else {
      // try for windows look for m$ losers (as they have a cow if things look different...)
      if (System.getProperty("os.name").matches("^.*?Windows.*$")) try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception e) { /* bah, nevermind */ }
      else {
        shortcutKeyMask = Event.ALT_MASK | Event.SHIFT_MASK | Event.SHIFT_MASK;
      }

      JMenu fileMenu = new JMenu(Strings.get("file"));

      fileMenu.add(new JMenuItem(new prefsActionClass(
          Strings.get("preferences"), KeyStroke.getKeyStroke(
          KeyEvent.VK_S, shortcutKeyMask)
      )));
      fileMenu.add(new JSeparator());
//      fileMenu.add(new JMenuItem(new printActionClass(
//          Strings.get("print"),
//          KeyStroke.getKeyStroke(KeyEvent.VK_P, shortcutKeyMask)
//      )));
//      fileMenu.add(new JSeparator());
      fileMenu.add(new JMenuItem(new exitActionClass(
          Strings.get("exit"),
          KeyStroke.getKeyStroke(KeyEvent.VK_Q, shortcutKeyMask)
      )));
      fileMenu.setEnabled(true);

      JMenu helpMenu = new JMenu(Strings.get("help"));
      helpMenu.add(new JMenuItem(new aboutActionClass(
          Strings.get("about"))
      ));

      mainMenuBar.add(fileMenu);
      mainMenuBar.add(helpMenu);
      // the alternate way of doing things here
    }

//        summary = new Summary();
//        frame.getContentPane().add(summary, BorderLayout.CENTER);
    frame.pack();
    frame.setMinimumSize(frame.getSize());


    Rectangle bounds = frame.getBounds();
    bounds.setSize(frame.getSize());
    frame.setBounds(bounds);

/*    final Dimension minSize = frame.getSize();
    frame.addComponentListener(new java.awt.event.ComponentAdapter() {
      public void componentResized(ComponentEvent e) {

        JFrame tmp = (JFrame)e.getSource();
        if (tmp.getWidth()<minSize.width)
          tmp.setSize(minSize.width,frame.getSize().height);
        if (tmp.getHeight()<minSize.height)
          tmp.setSize(frame.getSize().width,minSize.height);
      }
    });*/

//    login = new Login();
//    frame.getContentPane().add(login.getloginPanel(), BorderLayout.CENTER);

    frame.setSize(getWindowSize());

    frame.addWindowListener(new WindowListener() {
      public void windowOpened(WindowEvent windowEvent) {
      }

      public void windowClosing(WindowEvent windowEvent) {
        saveWindowSize(frame.getSize());
      }

      public void windowClosed(WindowEvent windowEvent) {
      }

      public void windowIconified(WindowEvent windowEvent) {
      }

      public void windowDeiconified(WindowEvent windowEvent) {
      }

      public void windowActivated(WindowEvent windowEvent) {
      }

      public void windowDeactivated(WindowEvent windowEvent) {
      }
    });

    System.setProperty("sun.rmi.loader.logLevel", "VERBOSE");
    System.setProperty("java.rmi.server.useCodebaseOnly", "true");

    appPrefs = new Preferences();
    appPrefs.setTitle(Strings.get("preferences"));
//    appPrefs.setLocation(new Point(frame.getLocation().x + 20, frame.getLocation().y + 20));

    appPrefs.pack();
    appPrefs.isClassServerValid();
    if (!appPrefs.areServerSettingsOk())
      appPrefs.setVisible(true);
    appPrefs.setSize(400, 400);

    //    frame.pack();
    frame.setVisible(true);
//    try {
//      Thread.currentThread().setContextClassLoader(RMIClassLoader.getClassLoader(ServerSettings.getInstance().getCodeBase().toString()));
//      System.out.println(((Controller)Naming.lookup(ServerSettings.getInstance().rmiNode() + "Controller")).invoke(new Command(Cmd.ping)));

      new Getter().get();
//    } catch (MalformedURLException e) {
//      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//    } catch (NotBoundException e) {
//      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//    } catch (RemoteException e) {
//      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//    }
  }

  public void aboutBox() {
    if (aboutBox == null) aboutBox = new AboutBox();
    aboutBox.setTitle(Strings.get("about"));
    aboutBox.setResizable(false);
    aboutBox.setLocation(new Point(frame.getLocation().x + 20, frame.getLocation().y + 20));
    aboutBox.setVisible(true);
  }


  public class aboutActionClass extends AbstractAction {
    public aboutActionClass(String text) {
      super(text);
    }

    public void actionPerformed(ActionEvent e) {
      aboutBox();
    }
  }

  public class printActionClass extends AbstractAction {
    public printActionClass(String text, KeyStroke shortcut) {
      super(text);
      putValue(ACCELERATOR_KEY, shortcut);
    }

    public void actionPerformed(ActionEvent e) {
    }
  }

  public class prefsActionClass extends AbstractAction {
    public prefsActionClass(String text, KeyStroke shortcut) {
      super(text);
      putValue(ACCELERATOR_KEY, shortcut);
    }

    public void actionPerformed(ActionEvent e) {
      appPrefs.setVisible(true);
    }
  }

  public class exitActionClass extends AbstractAction {
    public exitActionClass(String text, KeyStroke shortcut) {
      super(text);
      putValue(ACCELERATOR_KEY, shortcut);
    }

    public void actionPerformed(ActionEvent e) {
      saveWindowSize(frame.getSize());
      System.exit(0);
    }
  }

  private Dimension getWindowSize() {
    return new Dimension(
        new Integer(prefs.get("windowSizeX", "640").replaceAll("^(\\d+).*", "$1")),
        new Integer(prefs.get("windowSizeY", "480").replaceAll("^(\\d+).*", "$1")));
  }

  private void saveWindowSize(Dimension xy) {
    prefs.put("windowSizeX", String.valueOf(xy.getWidth()));
    prefs.put("windowSizeY", String.valueOf(xy.getHeight()));
  }

}
