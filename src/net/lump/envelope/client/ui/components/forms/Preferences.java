package us.lump.envelope.client.ui.components.forms;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import us.lump.envelope.client.portal.SecurityPortal;
import us.lump.envelope.client.ui.defs.Colors;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.client.ui.prefs.ServerSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.ResourceBundle;

public class Preferences extends JDialog {
  private JPanel prefsPane;
  private JButton ok;
  private JTabbedPane prefsTabs;
  private JTextField hostName;
  private JPanel okCancelGroup;
  private JPanel okCancelPanel;
  private JPanel tabPanel;
  private JLabel hostNameLabel;
  private JButton testButton;
  private JTextPane classStatusMessage;
  private JTextPane rmiStatusMessage;
  private JButton cancelButton;
  private JPanel serverTab;
  private JPanel serverFormPanel;
  private JPanel classServerStatusPanel;
  private JPanel rmiServerStatusPanel;
  private JPanel testButtonPanel;
  private JPanel loginTab;
  private JTextField userName;
  private JPasswordField password;
  private JCheckBox rememberPasswordCheckBox;
  private JButton logInButton;
  private JPanel loginPanel;
  private JLabel userNameLabel;
  private JLabel passwordLabel;
  private JLabel sessionStateLabel;
  private JLabel sessionState;
  private JCheckBox compress;
  private JCheckBox encrypt;
  private ServerSettings ssData = ServerSettings.getInstance();
  private LoginSettings lsData = LoginSettings.getInstance();
  private Boolean classServerValid = null;
  private Boolean rmiServerValid = null;

  private static Preferences singleton = null;
  private static boolean checkingLoginSettings = false;
  private boolean hadLoginSuccessYet = false;


  public static Preferences getInstance() {
    if (singleton == null) singleton = new Preferences();
    return singleton;
  }

  private Preferences() {

    setContentPane(prefsPane);
    setModal(true);
    getRootPane().setDefaultButton(ok);

    fillServerFormWithSavedData();
    fillUserFormWithSavedData();

    testButton.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        super.mousePressed(e);
        // reset the cache when we're explicitly checking
        ssData.resetCache();
        areServerSettingsOk();
      }
    });

    ok.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        boolean serverSettingsOk = areServerSettingsOk();
        boolean loginSettingsOk = areLoginSettingsOk();

        if (serverSettingsOk && loginSettingsOk)
          setVisible(false);
        else {
          JOptionPane.showMessageDialog(
              (Component)e.getSource(),
              Strings.get("error.settings_are_not_valid"),
              Strings.get("error"),
              JOptionPane.ERROR_MESSAGE);
          if (!loginSettingsOk && serverSettingsOk)
            selectTab(Strings.get("login"));
          else selectTab(Strings.get("server"));
        }
      }
    });
//    classPort.addKeyListener(new KeyAdapter() {
//      public void keyTyped(KeyEvent e) {
//        super.keyTyped(e);
//
//        if (String.valueOf(e.getKeyChar()).matches("^\\d$")) {
//        }
//      }
//    });
    cancelButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (areServerSettingsOk() && areLoginSettingsOk())
          setVisible(false);
        else
          System.exit(0);
      }
    });
    logInButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (areServerSettingsOk())
          areLoginSettingsOk();
        else {
          sessionState.setForeground(Colors.getColor("red"));
          sessionState.setText(Strings.get("error.server_settings_not_valid"));
        }
      }
    });

    compress.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ssData.setCompress(compress.isSelected());
        encrypt.setSelected(ssData.getEncrypt());
      }
    });
    encrypt.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ssData.setEncrypt(encrypt.isSelected());
        compress.setSelected(ssData.getCompress());
      }
    });

    prefsPane.setSize(prefsPane.getPreferredSize());
  }

  public void selectTab(String title) {
    for (int x = 0; x < prefsTabs.getTabCount(); x++) {
      if (prefsTabs.getTitleAt(x).equals(title)) {
        selectTab(x);
        break;
      }
    }
  }

  public void selectTab(int index) {
    if (prefsTabs.getSelectedIndex() != index)
      prefsTabs.setSelectedIndex(index);
  }

  public Boolean areLoginSettingsOk() {
    if (checkingLoginSettings) return null;
    checkingLoginSettings = true;

    SecurityPortal sp = new SecurityPortal();

    if (!hadLoginSuccessYet && !lsData.passwordIsSaved()
        && Arrays.equals(password.getPassword(), new char[0])) {
      sessionState.setForeground(Colors.getColor("red"));
      sessionState.setText(Strings.get("session.state.not.attempted"));
      checkingLoginSettings = false;
      return false;
    }

    Boolean authed = null;
    try {
      lsData.setUsername(userName.getText());
      lsData.setPassword(String.valueOf(password.getPassword()));
      lsData.setPasswordShouldBeSaved(rememberPasswordCheckBox.isSelected());
      authed = sp.auth(lsData.challengeResponse(sp.getChallenge()));
    } catch (Exception setException) {
      String message = null;
      Throwable cause = setException;
      while (cause != null) {
        message = cause.getMessage();
        cause = cause.getCause();
      }
      sessionState.setForeground(Colors.getColor("red"));
      sessionState.setText(message);
    }

    if (authed != null && authed) {
      hadLoginSuccessYet = true;
      sessionState.setForeground(Colors.getColor("green"));
      sessionState.setText(Strings.get("session.state.authorized"));
    } else {
      sessionState.setForeground(Colors.getColor("red"));
      sessionState.setText(Strings.get("session.state.invalid"));
    }

    checkingLoginSettings = false;
    return authed == null ? false : authed;
  }

  public boolean areServerSettingsOk() {
    // set the host/port
    ssData.setHostName(hostName.getText());

    rmiStatusMessage.setText(Strings.get("pending"));
    rmiStatusMessage.setForeground(Colors.getColor("gray"));
    rmiServerValid = false;

    String classTestResult = ssData.testClassServer();
    if (classTestResult.equals(Strings.get("ok"))) {
      classStatusMessage.setForeground(Colors.getColor("green"));
      classServerValid = true;
    } else {
      // default rmi server status to invalid while testing class server
      sessionState.setText(Strings.get("pending"));
      sessionState.setForeground(Colors.getColor("gray"));

      classStatusMessage.setForeground(Colors.getColor("red"));
      classServerValid = false;
    }
    classStatusMessage.setText(classTestResult);

    // rmi server test if class server is valid
    if (classServerValid) {
      String rmiTestResult = ssData.testRmiServer();
      if (rmiTestResult.equals(Strings.get("ok"))) {
        rmiStatusMessage.setForeground(Colors.getColor("green"));
        rmiServerValid = true;
      } else {
        rmiStatusMessage.setForeground(Colors.getColor("red"));
        rmiServerValid = false;
      }
      rmiStatusMessage.setText(rmiTestResult);
      return rmiServerValid;

    }

    if (classServerValid && rmiServerValid) {
      cancelButton.setEnabled(false);
      return true;
    } else {
      cancelButton.setEnabled(true);
      return false;
    }
  }


  public void fillServerFormWithSavedData() {
    if (System.getProperty("codebase") != null)
      hostName.setText(System.getProperty("codebase"));
    else
      hostName.setText(ssData.getHostName() + ":" + ssData.getClassPort());

    compress.setSelected(ssData.getCompress());
    encrypt.setSelected(ssData.getEncrypt());
  }

  public void fillUserFormWithSavedData() {
    userName.setText(lsData.getUsername());
    password.setText(lsData.getPassword());
    rememberPasswordCheckBox.setSelected(lsData.shouldPasswordBeSaved());
  }

  public boolean isModified(ServerSettings data) {
    if (hostName.getText() != null ? !hostName.getText()
        .equals(data.getHostName()) : data.getHostName() != null)
      return true;
    return false;
  }

  {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
    $$$setupUI$$$();
  }

  /**
   * Method generated by IntelliJ IDEA GUI Designer >>> IMPORTANT!! <<< DO NOT
   * edit this method OR call it in your code!
   */
  private void $$$setupUI$$$() {
    prefsPane = new JPanel();
    prefsPane.setLayout(new GridLayoutManager(2,
                                              1,
                                              new Insets(10, 10, 10, 10),
                                              -1,
                                              -1));
    okCancelPanel = new JPanel();
    okCancelPanel.setLayout(new GridLayoutManager(1,
                                                  3,
                                                  new Insets(0, 0, 0, 0),
                                                  -1,
                                                  -1));
    prefsPane.add(okCancelPanel, new GridConstraints(1,
                                                     0,
                                                     1,
                                                     1,
                                                     GridConstraints.ANCHOR_CENTER,
                                                     GridConstraints.FILL_BOTH,
                                                     GridConstraints
                                                         .SIZEPOLICY_CAN_SHRINK
                                                     | GridConstraints
                                                         .SIZEPOLICY_CAN_GROW,
                                                     1,
                                                     null,
                                                     null,
                                                     null,
                                                     0,
                                                     false));
    final Spacer spacer1 = new Spacer();
    okCancelPanel.add(spacer1, new GridConstraints(0,
                                                   0,
                                                   1,
                                                   1,
                                                   GridConstraints.ANCHOR_CENTER,
                                                   GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_WANT_GROW,
                                                   1,
                                                   null,
                                                   new Dimension(152, 11),
                                                   null,
                                                   0,
                                                   false));
    okCancelGroup = new JPanel();
    okCancelGroup.setLayout(new GridLayoutManager(1,
                                                  1,
                                                  new Insets(0, 0, 0, 0),
                                                  -1,
                                                  -1));
    okCancelPanel.add(okCancelGroup, new GridConstraints(0,
                                                         2,
                                                         1,
                                                         1,
                                                         GridConstraints.ANCHOR_CENTER,
                                                         GridConstraints.FILL_BOTH,
                                                         GridConstraints
                                                             .SIZEPOLICY_CAN_SHRINK
                                                         | GridConstraints
                                                             .SIZEPOLICY_CAN_GROW,
                                                         GridConstraints
                                                             .SIZEPOLICY_CAN_SHRINK
                                                         | GridConstraints
                                                             .SIZEPOLICY_CAN_GROW,
                                                         null,
                                                         null,
                                                         null,
                                                         0,
                                                         false));
    ok = new JButton();
    this.$$$loadButtonText$$$(ok, ResourceBundle.getBundle(
        "us/lump/envelope/client/ui/defs/Strings").getString("ok"));
    okCancelGroup.add(ok, new GridConstraints(0,
                                              0,
                                              1,
                                              1,
                                              GridConstraints.ANCHOR_CENTER,
                                              GridConstraints.FILL_HORIZONTAL,
                                              GridConstraints
                                                  .SIZEPOLICY_CAN_SHRINK
                                              | GridConstraints
                                                  .SIZEPOLICY_CAN_GROW,
                                              GridConstraints.SIZEPOLICY_FIXED,
                                              null,
                                              null,
                                              null,
                                              0,
                                              false));
    cancelButton = new JButton();
    this.$$$loadButtonText$$$(cancelButton, ResourceBundle.getBundle(
        "us/lump/envelope/client/ui/defs/Strings").getString("cancel"));
    okCancelPanel.add(cancelButton, new GridConstraints(0,
                                                        1,
                                                        1,
                                                        1,
                                                        GridConstraints.ANCHOR_CENTER,
                                                        GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints
                                                            .SIZEPOLICY_CAN_SHRINK
                                                        | GridConstraints
                                                            .SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED,
                                                        null,
                                                        null,
                                                        null,
                                                        0,
                                                        false));
    tabPanel = new JPanel();
    tabPanel.setLayout(new GridLayoutManager(1,
                                             1,
                                             new Insets(0, 0, 0, 0),
                                             -1,
                                             -1));
    prefsPane.add(tabPanel, new GridConstraints(0,
                                                0,
                                                1,
                                                1,
                                                GridConstraints.ANCHOR_CENTER,
                                                GridConstraints.FILL_BOTH,
                                                GridConstraints
                                                    .SIZEPOLICY_CAN_SHRINK
                                                | GridConstraints
                                                    .SIZEPOLICY_CAN_GROW,
                                                GridConstraints
                                                    .SIZEPOLICY_CAN_SHRINK
                                                | GridConstraints
                                                    .SIZEPOLICY_CAN_GROW,
                                                null,
                                                null,
                                                null,
                                                0,
                                                false));
    prefsTabs = new JTabbedPane();
    tabPanel.add(prefsTabs, new GridConstraints(0,
                                                0,
                                                1,
                                                1,
                                                GridConstraints.ANCHOR_CENTER,
                                                GridConstraints.FILL_BOTH,
                                                GridConstraints
                                                    .SIZEPOLICY_CAN_SHRINK
                                                | GridConstraints
                                                    .SIZEPOLICY_CAN_GROW,
                                                GridConstraints
                                                    .SIZEPOLICY_CAN_SHRINK
                                                | GridConstraints
                                                    .SIZEPOLICY_CAN_GROW,
                                                null,
                                                new Dimension(200, 200),
                                                null,
                                                0,
                                                false));
    serverTab = new JPanel();
    serverTab.setLayout(new GridLayoutManager(4,
                                              1,
                                              new Insets(5, 5, 5, 5),
                                              -1,
                                              -1));
    prefsTabs.addTab(ResourceBundle.getBundle(
        "us/lump/envelope/client/ui/defs/Strings").getString("server"),
                     serverTab);
    serverFormPanel = new JPanel();
    serverFormPanel.setLayout(new GridLayoutManager(3,
                                                    2,
                                                    new Insets(0, 0, 0, 0),
                                                    -1,
                                                    -1));
    serverTab.add(serverFormPanel, new GridConstraints(0,
                                                       0,
                                                       1,
                                                       1,
                                                       GridConstraints.ANCHOR_CENTER,
                                                       GridConstraints.FILL_BOTH,
                                                       GridConstraints
                                                           .SIZEPOLICY_CAN_SHRINK
                                                       | GridConstraints
                                                           .SIZEPOLICY_CAN_GROW,
                                                       GridConstraints
                                                           .SIZEPOLICY_CAN_SHRINK
                                                       | GridConstraints
                                                           .SIZEPOLICY_CAN_GROW,
                                                       null,
                                                       null,
                                                       null,
                                                       0,
                                                       false));
    hostNameLabel = new JLabel();
    this.$$$loadLabelText$$$(hostNameLabel, ResourceBundle.getBundle(
        "us/lump/envelope/client/ui/defs/Strings").getString("hostname"));
    serverFormPanel.add(hostNameLabel, new GridConstraints(0,
                                                           0,
                                                           1,
                                                           1,
                                                           GridConstraints.ANCHOR_EAST,
                                                           GridConstraints.FILL_NONE,
                                                           GridConstraints.SIZEPOLICY_FIXED,
                                                           GridConstraints.SIZEPOLICY_FIXED,
                                                           null,
                                                           null,
                                                           null,
                                                           0,
                                                           false));
    hostName = new JTextField();
    serverFormPanel.add(hostName, new GridConstraints(0,
                                                      1,
                                                      1,
                                                      1,
                                                      GridConstraints.ANCHOR_WEST,
                                                      GridConstraints.FILL_HORIZONTAL,
                                                      GridConstraints.SIZEPOLICY_WANT_GROW,
                                                      GridConstraints.SIZEPOLICY_FIXED,
                                                      null,
                                                      new Dimension(150, -1),
                                                      null,
                                                      0,
                                                      false));
    compress = new JCheckBox();
    this.$$$loadButtonText$$$(compress, ResourceBundle.getBundle(
        "us/lump/envelope/client/ui/defs/Strings").getString(
        "compress.with.blah"));
    serverFormPanel.add(compress, new GridConstraints(1,
                                                      1,
                                                      1,
                                                      1,
                                                      GridConstraints.ANCHOR_WEST,
                                                      GridConstraints.FILL_NONE,
                                                      GridConstraints
                                                          .SIZEPOLICY_CAN_SHRINK
                                                      | GridConstraints
                                                          .SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_FIXED,
                                                      null,
                                                      null,
                                                      null,
                                                      0,
                                                      false));
    encrypt = new JCheckBox();
    this.$$$loadButtonText$$$(encrypt, ResourceBundle.getBundle(
        "us/lump/envelope/client/ui/defs/Strings").getString("encrypt.with.blah"));
    serverFormPanel.add(encrypt, new GridConstraints(2,
                                                     1,
                                                     1,
                                                     1,
                                                     GridConstraints.ANCHOR_WEST,
                                                     GridConstraints.FILL_NONE,
                                                     GridConstraints
                                                         .SIZEPOLICY_CAN_SHRINK
                                                     | GridConstraints
                                                         .SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_FIXED,
                                                     null,
                                                     null,
                                                     null,
                                                     0,
                                                     false));
    classServerStatusPanel = new JPanel();
    classServerStatusPanel.setLayout(new GridLayoutManager(1,
                                                           1,
                                                           new Insets(3,
                                                                      3,
                                                                      3,
                                                                      3),
                                                           -1,
                                                           -1));
    serverTab.add(classServerStatusPanel, new GridConstraints(1,
                                                              0,
                                                              1,
                                                              1,
                                                              GridConstraints.ANCHOR_CENTER,
                                                              GridConstraints.FILL_BOTH,
                                                              GridConstraints
                                                                  .SIZEPOLICY_CAN_SHRINK
                                                              | GridConstraints
                                                                  .SIZEPOLICY_CAN_GROW,
                                                              GridConstraints
                                                                  .SIZEPOLICY_CAN_SHRINK
                                                              | GridConstraints
                                                                  .SIZEPOLICY_WANT_GROW,
                                                              null,
                                                              null,
                                                              null,
                                                              0,
                                                              false));
    classServerStatusPanel.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createEtchedBorder(),
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString(
            "class_server_status")));
    classStatusMessage = new JTextPane();
    classStatusMessage.setBackground(UIManager.getColor("Label.background"));
    classStatusMessage.setEditable(false);
    classStatusMessage.setForeground(UIManager.getColor("Label.foreground"));
    classStatusMessage.setText(ResourceBundle.getBundle(
        "us/lump/envelope/client/ui/defs/Strings").getString(
        "test_not_performed_yet"));
    classServerStatusPanel.add(classStatusMessage, new GridConstraints(0,
                                                                       0,
                                                                       1,
                                                                       1,
                                                                       GridConstraints.ANCHOR_NORTH,
                                                                       GridConstraints.FILL_HORIZONTAL,
                                                                       GridConstraints
                                                                           .SIZEPOLICY_CAN_SHRINK
                                                                       | GridConstraints
                                                                           .SIZEPOLICY_WANT_GROW,
                                                                       GridConstraints
                                                                           .SIZEPOLICY_CAN_SHRINK
                                                                       | GridConstraints
                                                                           .SIZEPOLICY_WANT_GROW,
                                                                       null,
                                                                       new Dimension(
                                                                           150,
                                                                           -1),
                                                                       null,
                                                                       0,
                                                                       false));
    rmiServerStatusPanel = new JPanel();
    rmiServerStatusPanel.setLayout(new GridLayoutManager(1,
                                                         1,
                                                         new Insets(0, 0, 0, 0),
                                                         -1,
                                                         -1));
    serverTab.add(rmiServerStatusPanel, new GridConstraints(2,
                                                            0,
                                                            1,
                                                            1,
                                                            GridConstraints.ANCHOR_CENTER,
                                                            GridConstraints.FILL_BOTH,
                                                            GridConstraints
                                                                .SIZEPOLICY_CAN_SHRINK
                                                            | GridConstraints
                                                                .SIZEPOLICY_CAN_GROW,
                                                            GridConstraints
                                                                .SIZEPOLICY_CAN_SHRINK
                                                            | GridConstraints
                                                                .SIZEPOLICY_WANT_GROW,
                                                            null,
                                                            null,
                                                            null,
                                                            0,
                                                            false));
    rmiServerStatusPanel.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createEtchedBorder(),
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString(
            "rmi_server_status")));
    rmiStatusMessage = new JTextPane();
    rmiStatusMessage.setBackground(UIManager.getColor("Label.background"));
    rmiStatusMessage.setEditable(false);
    rmiStatusMessage.setForeground(UIManager.getColor("Label.foreground"));
    rmiStatusMessage.setText(ResourceBundle.getBundle(
        "us/lump/envelope/client/ui/defs/Strings").getString(
        "test_not_performed_yet"));
    rmiServerStatusPanel.add(rmiStatusMessage, new GridConstraints(0,
                                                                   0,
                                                                   1,
                                                                   1,
                                                                   GridConstraints.ANCHOR_NORTH,
                                                                   GridConstraints.FILL_HORIZONTAL,
                                                                   GridConstraints
                                                                       .SIZEPOLICY_CAN_SHRINK
                                                                   | GridConstraints
                                                                       .SIZEPOLICY_WANT_GROW,
                                                                   GridConstraints
                                                                       .SIZEPOLICY_CAN_SHRINK
                                                                   | GridConstraints
                                                                       .SIZEPOLICY_WANT_GROW,
                                                                   null,
                                                                   new Dimension(
                                                                       150,
                                                                       -1),
                                                                   null,
                                                                   0,
                                                                   false));
    testButtonPanel = new JPanel();
    testButtonPanel.setLayout(new GridLayoutManager(1,
                                                    2,
                                                    new Insets(0, 0, 0, 0),
                                                    -1,
                                                    -1));
    serverTab.add(testButtonPanel, new GridConstraints(3,
                                                       0,
                                                       1,
                                                       1,
                                                       GridConstraints.ANCHOR_CENTER,
                                                       GridConstraints.FILL_BOTH,
                                                       GridConstraints
                                                           .SIZEPOLICY_CAN_SHRINK
                                                       | GridConstraints
                                                           .SIZEPOLICY_CAN_GROW,
                                                       GridConstraints
                                                           .SIZEPOLICY_CAN_SHRINK
                                                       | GridConstraints
                                                           .SIZEPOLICY_CAN_GROW,
                                                       null,
                                                       null,
                                                       null,
                                                       0,
                                                       false));
    testButton = new JButton();
    this.$$$loadButtonText$$$(testButton, ResourceBundle.getBundle(
        "us/lump/envelope/client/ui/defs/Strings").getString("test_settings"));
    testButtonPanel.add(testButton, new GridConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        GridConstraints.ANCHOR_CENTER,
                                                        GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_FIXED,
                                                        GridConstraints.SIZEPOLICY_FIXED,
                                                        null,
                                                        null,
                                                        null,
                                                        0,
                                                        false));
    final Spacer spacer2 = new Spacer();
    testButtonPanel.add(spacer2, new GridConstraints(0,
                                                     1,
                                                     1,
                                                     1,
                                                     GridConstraints.ANCHOR_CENTER,
                                                     GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_WANT_GROW,
                                                     1,
                                                     null,
                                                     null,
                                                     null,
                                                     0,
                                                     false));
    loginTab = new JPanel();
    loginTab.setLayout(new GridLayoutManager(3,
                                             3,
                                             new Insets(0, 0, 0, 0),
                                             -1,
                                             -1));
    prefsTabs.addTab(ResourceBundle.getBundle(
        "us/lump/envelope/client/ui/defs/Strings").getString("login"),
                     loginTab);
    loginPanel = new JPanel();
    loginPanel.setLayout(new GridLayoutManager(5,
                                               2,
                                               new Insets(0, 0, 0, 0),
                                               -1,
                                               -1));
    loginTab.add(loginPanel, new GridConstraints(1,
                                                 1,
                                                 1,
                                                 1,
                                                 GridConstraints.ANCHOR_CENTER,
                                                 GridConstraints.FILL_BOTH,
                                                 GridConstraints
                                                     .SIZEPOLICY_CAN_SHRINK
                                                 | GridConstraints
                                                     .SIZEPOLICY_CAN_GROW,
                                                 GridConstraints
                                                     .SIZEPOLICY_CAN_SHRINK
                                                 | GridConstraints
                                                     .SIZEPOLICY_CAN_GROW,
                                                 null,
                                                 null,
                                                 null,
                                                 0,
                                                 false));
    userNameLabel = new JLabel();
    userNameLabel.setHorizontalAlignment(11);
    userNameLabel.setHorizontalTextPosition(0);
    this.$$$loadLabelText$$$(userNameLabel, ResourceBundle.getBundle(
        "us/lump/envelope/client/ui/defs/Strings").getString("username"));
    loginPanel.add(userNameLabel, new GridConstraints(0,
                                                      0,
                                                      1,
                                                      1,
                                                      GridConstraints.ANCHOR_EAST,
                                                      GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_FIXED,
                                                      GridConstraints.SIZEPOLICY_FIXED,
                                                      null,
                                                      null,
                                                      null,
                                                      0,
                                                      false));
    userName = new JTextField();
    loginPanel.add(userName, new GridConstraints(0,
                                                 1,
                                                 1,
                                                 1,
                                                 GridConstraints.ANCHOR_WEST,
                                                 GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_WANT_GROW,
                                                 GridConstraints.SIZEPOLICY_FIXED,
                                                 null,
                                                 new Dimension(150, -1),
                                                 null,
                                                 0,
                                                 false));
    passwordLabel = new JLabel();
    passwordLabel.setHorizontalAlignment(11);
    this.$$$loadLabelText$$$(passwordLabel, ResourceBundle.getBundle(
        "us/lump/envelope/client/ui/defs/Strings").getString("password"));
    loginPanel.add(passwordLabel, new GridConstraints(1,
                                                      0,
                                                      1,
                                                      1,
                                                      GridConstraints.ANCHOR_EAST,
                                                      GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_FIXED,
                                                      GridConstraints.SIZEPOLICY_FIXED,
                                                      null,
                                                      null,
                                                      null,
                                                      0,
                                                      false));
    password = new JPasswordField();
    loginPanel.add(password, new GridConstraints(1,
                                                 1,
                                                 1,
                                                 1,
                                                 GridConstraints.ANCHOR_WEST,
                                                 GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_WANT_GROW,
                                                 GridConstraints.SIZEPOLICY_FIXED,
                                                 null,
                                                 new Dimension(150, -1),
                                                 null,
                                                 0,
                                                 false));
    rememberPasswordCheckBox = new JCheckBox();
    this.$$$loadButtonText$$$(rememberPasswordCheckBox,
                              ResourceBundle.getBundle(
                                  "us/lump/envelope/client/ui/defs/Strings").getString(
                                  "remember.password"));
    loginPanel.add(rememberPasswordCheckBox, new GridConstraints(2,
                                                                 1,
                                                                 1,
                                                                 1,
                                                                 GridConstraints.ANCHOR_WEST,
                                                                 GridConstraints.FILL_NONE,
                                                                 GridConstraints
                                                                     .SIZEPOLICY_CAN_SHRINK
                                                                 | GridConstraints
                                                                     .SIZEPOLICY_CAN_GROW,
                                                                 GridConstraints.SIZEPOLICY_FIXED,
                                                                 null,
                                                                 null,
                                                                 null,
                                                                 0,
                                                                 false));
    sessionStateLabel = new JLabel();
    sessionStateLabel.setHorizontalAlignment(11);
    this.$$$loadLabelText$$$(sessionStateLabel, ResourceBundle.getBundle(
        "us/lump/envelope/client/ui/defs/Strings").getString("session.state"));
    loginPanel.add(sessionStateLabel, new GridConstraints(3,
                                                          0,
                                                          1,
                                                          1,
                                                          GridConstraints.ANCHOR_EAST,
                                                          GridConstraints.FILL_NONE,
                                                          GridConstraints.SIZEPOLICY_FIXED,
                                                          GridConstraints.SIZEPOLICY_FIXED,
                                                          null,
                                                          null,
                                                          null,
                                                          0,
                                                          false));
    sessionState = new JLabel();
    this.$$$loadLabelText$$$(sessionState, ResourceBundle.getBundle(
        "us/lump/envelope/client/ui/defs/Strings").getString("unknown"));
    loginPanel.add(sessionState, new GridConstraints(3,
                                                     1,
                                                     1,
                                                     1,
                                                     GridConstraints.ANCHOR_WEST,
                                                     GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED,
                                                     GridConstraints.SIZEPOLICY_FIXED,
                                                     null,
                                                     null,
                                                     null,
                                                     0,
                                                     false));
    logInButton = new JButton();
    this.$$$loadButtonText$$$(logInButton, ResourceBundle.getBundle(
        "us/lump/envelope/client/ui/defs/Strings").getString("log.in"));
    loginPanel.add(logInButton, new GridConstraints(4,
                                                    1,
                                                    1,
                                                    1,
                                                    GridConstraints.ANCHOR_CENTER,
                                                    GridConstraints.FILL_HORIZONTAL,
                                                    GridConstraints
                                                        .SIZEPOLICY_CAN_SHRINK
                                                    | GridConstraints
                                                        .SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_FIXED,
                                                    null,
                                                    null,
                                                    null,
                                                    0,
                                                    false));
    final Spacer spacer3 = new Spacer();
    loginTab.add(spacer3, new GridConstraints(2,
                                              1,
                                              1,
                                              1,
                                              GridConstraints.ANCHOR_CENTER,
                                              GridConstraints.FILL_VERTICAL,
                                              1,
                                              GridConstraints.SIZEPOLICY_WANT_GROW,
                                              null,
                                              null,
                                              null,
                                              0,
                                              false));
    final Spacer spacer4 = new Spacer();
    loginTab.add(spacer4, new GridConstraints(0,
                                              1,
                                              1,
                                              1,
                                              GridConstraints.ANCHOR_CENTER,
                                              GridConstraints.FILL_VERTICAL,
                                              1,
                                              GridConstraints.SIZEPOLICY_WANT_GROW,
                                              null,
                                              null,
                                              null,
                                              0,
                                              false));
    final Spacer spacer5 = new Spacer();
    loginTab.add(spacer5, new GridConstraints(1,
                                              2,
                                              1,
                                              1,
                                              GridConstraints.ANCHOR_CENTER,
                                              GridConstraints.FILL_HORIZONTAL,
                                              GridConstraints.SIZEPOLICY_WANT_GROW,
                                              1,
                                              null,
                                              null,
                                              null,
                                              0,
                                              false));
    final Spacer spacer6 = new Spacer();
    loginTab.add(spacer6, new GridConstraints(1,
                                              0,
                                              1,
                                              1,
                                              GridConstraints.ANCHOR_CENTER,
                                              GridConstraints.FILL_HORIZONTAL,
                                              GridConstraints.SIZEPOLICY_WANT_GROW,
                                              1,
                                              null,
                                              null,
                                              null,
                                              0,
                                              false));
  }

  /** @noinspection ALL */
  private void $$$loadLabelText$$$(JLabel component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setDisplayedMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  private void $$$loadButtonText$$$(AbstractButton component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  public JComponent $$$getRootComponent$$$() { return prefsPane; }
}
