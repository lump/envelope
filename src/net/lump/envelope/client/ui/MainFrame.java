package us.lump.envelope.client.ui;

import com.apple.eawt.Application;
import com.apple.eawt.ApplicationEvent;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.client.CriteriaFactory;
import us.lump.envelope.client.State;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.*;

/**
 * The main frame for the application.
 *
 * @author Troy Bowman
 * @version $Id: MainFrame.java,v 1.1 2008/07/06 04:14:24 troy Exp $
 */
public class MainFrame extends JFrame {
  private AboutBox aboutBox;
  private Preferences appPrefs;
  private java.util.prefs.Preferences prefs
      = java.util.prefs.Preferences.userNodeForPackage(this.getClass());
  static final JMenuBar mainMenuBar = new JMenuBar();

  //content
  private JScrollPane treeScrollPane = new JScrollPane();

  private JScrollPane tableScrollPane = new JScrollPane();
  private JTable transactionTable = new JTable();
  private JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                                treeScrollPane,
                                                tableScrollPane);
  private JLabel status = new JLabel(Strings.get("initializing"));

  private static State state = State.getInstance();

  public static MainFrame getInstance() {
    if (state.getMainFrame() == null)
      state.setMainFrame(new MainFrame());
    return state.getMainFrame();
  }

  private MainFrame() {
    this.setTitle(Strings.get("envelope_budget"));

    splitPane.setResizeWeight(.3);
    splitPane.getLeftComponent().setMinimumSize(new Dimension(100, 0));
    splitPane.getRightComponent().setMinimumSize(new Dimension(300, 0));
    splitPane.setContinuousLayout(true);
    splitPane.setOneTouchExpandable(true);

    status.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

    tableScrollPane.add(transactionTable);

    this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    this.setLayout(new BorderLayout());
    this.setJMenuBar(mainMenuBar);
    this.getContentPane().add(BorderLayout.CENTER, splitPane);
    this.getContentPane().add(BorderLayout.SOUTH, status);

    int shortcutKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    if (System.getProperty("os.name").toLowerCase()
        .matches("^.*?mac os x.*$")) {
      // the Mac specific code here
      System.getProperties().put("apple.laf.useScreenMenuBar", true);

      Application fApplication = Application.getApplication();
      fApplication.setEnabledPreferencesMenu(true);
      fApplication.addApplicationListener(
          new com.apple.eawt.ApplicationAdapter() {

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
      // try for windows look for m$ losers (as they have a cow if things
      // look different...)
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

    setMinimumSize(getSize());

    Rectangle bounds = getBounds();
    bounds.setSize(getSize());
    setBounds(bounds);

    System.setProperty("sun.rmi.loader.logLevel", "VERBOSE");
    System.setProperty("java.rmi.server.useCodebaseOnly", "true");

    appPrefs = Preferences.getInstance();
    appPrefs.setTitle(Strings.get("preferences"));

    appPrefs.pack();
    if (!appPrefs.areServerSettingsOk()) {
      appPrefs.selectTab(Strings.get("server"));
      appPrefs.setVisible(true);
    }

    if (!appPrefs.areLoginSettingsOk()) {
      appPrefs.selectTab(Strings.get("login"));
      appPrefs.setVisible(true);
    }


    state.setBudget(
        CriteriaFactory.getInstance().getBudgetForUser(
            LoginSettings.getInstance().getUsername()));

    state.setHierarchy(Hierarchy.getInstance());
    state.getHierarchy().setRootNode(state.getBudget());

    treeScrollPane.setViewportView(state.getHierarchy());


    //    frame.pack();
    validate();
    pack();
    setSize(getWindowSize());
    setStatus(Strings.get("ready"));
    setVisible(true);
    repaint();
    RepaintManager.currentManager(this).paintDirtyRegions();
  }

  public Preferences getPreferences() {
    return appPrefs;
  }


  public void aboutBox() {
    if (aboutBox == null) aboutBox = new AboutBox();
    aboutBox.setTitle(Strings.get("about"));
    aboutBox.setResizable(false);
    aboutBox.setLocation(new Point(getLocation().x + 20,
                                   getLocation().y + 20));
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

  public void setStatus(String status) {
    this.status.setText(status);
  }

  public class exitActionClass extends AbstractAction {
    public exitActionClass(String text, KeyStroke shortcut) {
      super(text);
      putValue(ACCELERATOR_KEY, shortcut);
    }

    public void actionPerformed(ActionEvent e) {
      exit(0);
    }
  }

  void exit(int value) {
    saveWindowSize(getSize());
    System.exit(value);
  }

  private Dimension getWindowSize() {
    return new Dimension(
        new Integer(prefs.get("windowSizeX", "640")
            .replaceAll("^(\\d+).*", "$1")),
        new Integer(prefs.get("windowSizeY", "480")
            .replaceAll("^(\\d+).*", "$1")));
  }

  private void saveWindowSize(Dimension xy) {
    prefs.put("windowSizeX", String.valueOf(xy.getWidth()));
    prefs.put("windowSizeY", String.valueOf(xy.getHeight()));
  }

}
