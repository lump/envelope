package us.lump.envelope.client.ui;

import org.apache.log4j.BasicConfigurator;
import us.lump.envelope.client.State;
import us.lump.envelope.client.thread.StatusElement;
import us.lump.envelope.client.ui.components.AboutBox;
import us.lump.envelope.client.ui.components.Hierarchy;
import us.lump.envelope.client.ui.components.StatusBar;
import us.lump.envelope.client.ui.components.forms.Preferences;
import us.lump.envelope.client.ui.components.forms.TableQueryBar;
import us.lump.envelope.client.ui.components.forms.TransactionForm;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.client.ui.images.ImageResource;
import us.lump.lib.util.EmacsKeyBindings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * The main frame for the application.
 *
 * @author Troy Bowman
 * @version $Id: MainFrame.java,v 1.35 2009/01/21 06:52:29 troy Exp $
 */
public class MainFrame extends JFrame {
  private AboutBox aboutBox;
  private Preferences appPrefs;
  private java.util.prefs.Preferences prefs
      = java.util.prefs.Preferences.userNodeForPackage(this.getClass());
  static final JMenuBar mainMenuBar = new JMenuBar();
  private static MainFrame singleton;
  TransactionForm transactionForm = new TransactionForm();
  JCheckBoxMenuItem viewTransaction;
  int savedTransactionFormSplitterLocation = 0;

  //content
  private JScrollPane treeScrollPane = new JScrollPane();
  private JPanel contentPane = new JPanel(new BorderLayout());
  private JSplitPane tableContentSplitPane =
      new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                     Box.createVerticalGlue(),
                     null);
  private JSplitPane treeContentSplitPane =
      new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                     treeScrollPane,
                     tableContentSplitPane);


  public void setTablePane(JPanel p) {
    tableContentSplitPane.setTopComponent(p);
  }

  public boolean isTransactionViewShowing() {
    return viewTransaction.isSelected();
  }

  public void setTransactionViewShowing(boolean showing) {
    if (showing == isTransactionViewShowing()) return;
    viewTransaction.setSelected(showing);
    doViewTransaction();
  }

//  public void setDetailPane(JPanel p) {
//    tableContentSplitPane.setBottomComponent(p);
//    tableContentSplitPane.setDividerLocation(0.4);
//  }

  private StatusBar status = StatusBar.getInstance();

  private static State state = State.getInstance();

  synchronized public static MainFrame getInstance() {
    if (singleton == null) singleton = new MainFrame();
    return singleton;
  }

  private MainFrame() {

    StatusElement initStatus = status.addTask(Strings.get("initializing"));

    BasicConfigurator.configure();
    EmacsKeyBindings.loadEmacsKeyBindings();

    this.setTitle(Strings.get("envelope.budget"));
    this.setIconImages(ImageResource.getFrameList());
//    this.setIconImage(ImageResource.icon.envelope_32.getImage());

    treeContentSplitPane.setResizeWeight(0);
    treeContentSplitPane.getLeftComponent()
        .setMinimumSize(new Dimension(200, 0));
    treeContentSplitPane.setContinuousLayout(true);
    treeContentSplitPane.setOneTouchExpandable(true);

    tableContentSplitPane.setResizeWeight(1);
    tableContentSplitPane.setOneTouchExpandable(false);
    tableContentSplitPane.setContinuousLayout(true);
    tableContentSplitPane.setTopComponent(TableQueryBar.getInstance().getTableQueryPanel());


    this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    this.setLayout(new BorderLayout());
    this.getContentPane().add(BorderLayout.CENTER, treeContentSplitPane);
    this.getContentPane().add(BorderLayout.SOUTH, status);

    int shortcutKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

    viewTransaction = new JCheckBoxMenuItem(Strings.get("transaction"));
    tableContentSplitPane.setBottomComponent(transactionForm.getTransactionFormPanel());
    transactionForm.getTransactionFormPanel().setVisible(false);
    tableContentSplitPane.setDividerSize(0);

    viewTransaction.addActionListener(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        doViewTransaction();
      }
    });

    boolean mac = false;
    if (System.getProperty("mrj.version") != null) {
      AppleStuff apple = new AppleStuff();
      mac = true;
    }
    
    this.setJMenuBar(mainMenuBar);
    JMenu fileMenu = new JMenu(Strings.get("file"));
    JMenu viewMenu = new JMenu(Strings.get("view"));
    viewMenu.add(viewTransaction);

    mainMenuBar.add(fileMenu);
    mainMenuBar.add(viewMenu);

    if (!mac) {
      fileMenu.add(new JMenuItem(new prefsActionClass(
          Strings.get("preferences"), KeyStroke.getKeyStroke(
              KeyEvent.VK_S, shortcutKeyMask)
      )));
      fileMenu.add(new JSeparator());

      fileMenu.add(new JMenuItem(new exitActionClass(
          Strings.get("exit"),
          KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_MASK)
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


    Hierarchy.getInstance().refreshTree(state.getBudget());

    treeScrollPane.setViewportView(Hierarchy.getInstance());
    treeScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

    // hack to get stupid tree to resize width
    treeContentSplitPane.addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName()
            .equals(JSplitPane.DIVIDER_LOCATION_PROPERTY)) {
          Hierarchy.getInstance().configureLayoutCache();
        }
      }
    });
//    treeScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);


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
    treeContentSplitPane.setDividerLocation(prefs.getInt("splitPaneLocation",
                                                         treeContentSplitPane.getDividerLocation()));
    setVisible(true);
    status.removeTask(initStatus);
  }

  public Preferences getPreferences() {
    return appPrefs;
  }

  public void doViewTransaction() {
    if (viewTransaction.isSelected()) {

      if (savedTransactionFormSplitterLocation == 0)
        tableContentSplitPane.setDividerLocation(0.4);
      else
        tableContentSplitPane
            .setDividerLocation(savedTransactionFormSplitterLocation);

      tableContentSplitPane.setDividerSize((Integer)UIManager.get(
          "SplitPane.dividerSize"));
      transactionForm.getTransactionFormPanel().setVisible(true);
    } else {
      savedTransactionFormSplitterLocation =
          tableContentSplitPane.getDividerLocation();
      tableContentSplitPane.setDividerSize(0);
      transactionForm.getTransactionFormPanel().setVisible(false);
    }
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
    prefs.putInt("splitPaneLocation",
                 treeContentSplitPane.getDividerLocation());
    prefs.putInt("windowSizeX", getWidth());
    prefs.putInt("windowSizeY", getHeight());
    System.exit(value);
  }
}
