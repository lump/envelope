package us.lump.envelope.client;

import com.apple.eawt.Application;
import com.apple.eawt.ApplicationEvent;
import us.lump.envelope.client.portal.Getter;
import us.lump.envelope.client.ui.AboutBox;
import us.lump.envelope.client.ui.Preferences;
import us.lump.envelope.client.ui.defs.Strings;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * .
 *
 * @author troy
 * @version $Id: Main.java,v 1.2 2007/07/26 02:55:23 troy Exp $
 */
public class Main implements Runnable {
  private JFrame frame = new JFrame(Strings.get("envelope_budget"));
  private AboutBox aboutBox;
  private Preferences appPrefs;
  private java.util.prefs.Preferences prefs;
  static final JMenuBar mainMenuBar = new JMenuBar();

  //content
  private JScrollPane treeScrollPane = new JScrollPane();
  private JTree heirarchyTree = new JTree();
  private JScrollPane tableScrollPane = new JScrollPane();
  private JTable transactionTable = new JTable();
  private JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScrollPane, tableScrollPane);
  private JLabel status = new JLabel(Strings.get("ready"));

  {
    prefs = java.util.prefs.Preferences.userNodeForPackage(this.getClass());
  }

  public void run() {

    splitPane.setResizeWeight(.3);
    splitPane.getLeftComponent().setMinimumSize(new Dimension(100,0));
    splitPane.getRightComponent().setMinimumSize(new Dimension(300,0));
    splitPane.setContinuousLayout(true);
    splitPane.setOneTouchExpandable(true);

    status.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

    treeScrollPane.add(heirarchyTree);
    tableScrollPane.add(transactionTable);

    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);    
    frame.setLayout(new BorderLayout());
    frame.setJMenuBar(mainMenuBar);
    frame.getContentPane().add(BorderLayout.CENTER, splitPane);
    frame.getContentPane().add(BorderLayout.SOUTH, status);


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
          exit(0);
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
    }

    frame.setMinimumSize(frame.getSize());

    Rectangle bounds = frame.getBounds();
    bounds.setSize(frame.getSize());
    frame.setBounds(bounds);

    System.setProperty("sun.rmi.loader.logLevel", "VERBOSE");
    System.setProperty("java.rmi.server.useCodebaseOnly", "true");

    appPrefs = new Preferences();
    appPrefs.setTitle(Strings.get("preferences"));

    appPrefs.pack();
    if (!appPrefs.areServerSettingsOk())
      appPrefs.setVisible(true);
    appPrefs.setSize(400, 400);

    //    frame.pack();
    frame.validate();
    frame.setSize(getWindowSize());
    frame.setVisible(true);
    new Getter().get();
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
      Main.this.exit(0);
    }
  }

  void exit(int value) {
    saveWindowSize(frame.getSize());
    System.exit(value);
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
