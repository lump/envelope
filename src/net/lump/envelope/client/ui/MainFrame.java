package us.lump.envelope.client.ui;

import com.apple.eawt.Application;
import com.apple.eawt.ApplicationEvent;
import org.apache.log4j.BasicConfigurator;
import us.lump.envelope.client.State;
import us.lump.envelope.client.thread.StatusElement;
import us.lump.envelope.client.ui.components.AboutBox;
import us.lump.envelope.client.ui.components.Hierarchy;
import us.lump.envelope.client.ui.components.StatusBar;
import us.lump.envelope.client.ui.components.forms.Preferences;
import us.lump.envelope.client.ui.components.forms.TransactionForm;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.client.ui.images.ImageResource;
import us.lump.lib.util.EmacsKeyBindings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * The main frame for the application.
 *
 * @author Troy Bowman
 * @version $Id: MainFrame.java,v 1.19 2008/09/06 05:38:13 troy Exp $
 */
public class MainFrame extends JFrame {
  private AboutBox aboutBox;
  private Preferences appPrefs;
  private java.util.prefs.Preferences prefs
      = java.util.prefs.Preferences.userNodeForPackage(this.getClass());
  static final JMenuBar mainMenuBar = new JMenuBar();

  //content
  private JScrollPane treeScrollPane = new JScrollPane();
  private JPanel contentPane = new JPanel(new BorderLayout());
  private JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                                treeScrollPane, null);

  public void setContentPane(JPanel p) {
    splitPane.setRightComponent(p);
    splitPane.getRightComponent().setMinimumSize(new Dimension(300, 0));
  }

  private StatusBar status = StatusBar.getInstance();

  private static State state = State.getInstance();

  public static MainFrame getInstance() {
    if (state.getMainFrame() == null)
      state.setMainFrame(new MainFrame());
    return state.getMainFrame();
  }

  private MainFrame() {
    StatusElement initStatus = status.addTask(Strings.get("initializing"));

    BasicConfigurator.configure();
    EmacsKeyBindings.loadEmacsKeyBindings();

    this.setTitle(Strings.get("envelope_budget"));
    this.setIconImage(ImageResource.icon.envelope.getImage());

    splitPane.setResizeWeight(0);
    splitPane.getLeftComponent().setMinimumSize(new Dimension(100, 0));
    splitPane.setContinuousLayout(true);
    splitPane.setOneTouchExpandable(true);


    this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    this.setLayout(new BorderLayout());
    this.setJMenuBar(mainMenuBar);
    this.getContentPane().add(BorderLayout.CENTER, splitPane);
    this.getContentPane().add(BorderLayout.SOUTH, status);

    int shortcutKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

    JMenuItem addTransaction = new JMenuItem(Strings.get("new.transaction"));
    addTransaction.addActionListener(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        TransactionForm tf = new TransactionForm();
        setContentPane(tf.getTransactionFormPanel());
      }
    });

    JMenu fileMenu = new JMenu(Strings.get("file"));
    fileMenu.add(addTransaction);

    mainMenuBar.add(fileMenu);


    if (System.getProperty("os.name").toLowerCase()
        .matches("^.*?mac os x.*$")) {
      // the Mac specific code here
      System.getProperties().put("apple.laf.useScreenMenuBar", true);
      System.getProperties().put("com.apple.macos.useScreenMenuBar", true);

      Application application = Application.getApplication();
      application.setEnabledPreferencesMenu(true);
      application.addApplicationListener(
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
        shortcutKeyMask = Event.ALT_MASK | Event.SHIFT_MASK;

//        try {
//          UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
//        }
//        catch (Exception e) {
//          try {
//            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
//          }
//          catch (Exception ex) { }
//        }
      }

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

      mainMenuBar.add(helpMenu);
    }


    setMinimumSize(getSize());

    Rectangle bounds = getBounds();
    bounds.setSize(getSize());
    setBounds(bounds);

//    System.setProperty("sun.rmi.loader.logLevel", "VERBOSE");
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


    state.setHierarchy(Hierarchy.getInstance());
    state.getHierarchy().refreshTree(state.getBudget());

    treeScrollPane.setViewportView(state.getHierarchy());


    addWindowListener(new WindowListener() {
      public void windowOpened(WindowEvent e) {}

      public void windowClosing(WindowEvent e) { exit(0); }

      public void windowClosed(WindowEvent e) {}

      public void windowIconified(WindowEvent e) {}

      public void windowDeiconified(WindowEvent e) {}

      public void windowActivated(WindowEvent e) {}

      public void windowDeactivated(WindowEvent e) {}
    });


    addComponentListener(new ComponentListener() {

      public void componentResized(ComponentEvent e) {
//        System.err.println(e);
      }

      public void componentMoved(ComponentEvent e) {
//        System.err.println(e);
      }

      public void componentShown(ComponentEvent e) {
//        System.err.println(e);
      }

      public void componentHidden(ComponentEvent e) {
//        System.err.println(e);
      }
    });

    //    frame.pack();
    validate();
    pack();
    setSize(new Dimension(prefs.getInt("windowSizeX", 640),
                          prefs.getInt("windowSizeY", 480)));
    splitPane.setDividerLocation(prefs.getInt("splitPaneLocation",
                                              splitPane.getDividerLocation()));
    setVisible(true);
    status.removeTask(initStatus);
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
    prefs.putInt("splitPaneLocation", splitPane.getDividerLocation());
    prefs.putInt("windowSizeX", getWidth());
    prefs.putInt("windowSizeY", getHeight());
    System.exit(value);
  }
}
