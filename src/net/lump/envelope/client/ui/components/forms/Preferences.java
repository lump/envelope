package us.lump.envelope.client.ui.components.forms;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import us.lump.envelope.client.portal.SecurityPortal;
import us.lump.envelope.client.ui.defs.Colors;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.client.ui.images.ImageResource;
import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.client.ui.prefs.ServerSettings;
import us.lump.envelope.shared.command.security.Challenge;
import us.lump.envelope.shared.exception.AbortException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
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
  private JTextPane serverStatusMessage;
  private JButton cancelButton;
  private JPanel serverTab;
  private JPanel serverFormPanel;
  private JPanel serverStatusPanel;
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
  private JTextField context;
  private JLabel contextLabel;
  private ServerSettings ssData = ServerSettings.getInstance();
  private LoginSettings lsData = LoginSettings.getInstance();
  private Boolean classServerValid = null;

  private static Preferences singleton = null;
  private boolean hadLoginSuccessYet = false; {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
    $$$setupUI$$$();
  }

  /** Method generated by IntelliJ IDEA GUI Designer >>> IMPORTANT!! <<< DO NOT edit this method OR call it in your code! */
  private void $$$setupUI$$$() {
    prefsPane = new JPanel();
    prefsPane.setLayout(new GridLayoutManager(2, 1, new Insets(10, 10, 10, 10), -1, -1));
    okCancelPanel = new JPanel();
    okCancelPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    prefsPane.add(okCancelPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    okCancelPanel.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, new Dimension(152, 11), null, 0, false));
    okCancelGroup = new JPanel();
    okCancelGroup.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    okCancelPanel.add(okCancelGroup, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    ok = new JButton();
    this.$$$loadButtonText$$$(ok, ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("ok"));
    okCancelGroup.add(ok, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
        null, 0, false));
    cancelButton = new JButton();
    this.$$$loadButtonText$$$(cancelButton,
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("cancel"));
    okCancelPanel.add(cancelButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
        null, 0, false));
    tabPanel = new JPanel();
    tabPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    prefsPane.add(tabPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    prefsTabs = new JTabbedPane();
    tabPanel.add(prefsTabs, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0,
        false));
    serverTab = new JPanel();
    serverTab.setLayout(new GridLayoutManager(3, 1, new Insets(5, 5, 5, 5), -1, -1));
    prefsTabs.addTab(ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("server"), serverTab);
    serverFormPanel = new JPanel();
    serverFormPanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
    serverTab.add(serverFormPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    hostNameLabel = new JLabel();
    this.$$$loadLabelText$$$(hostNameLabel,
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("hostname"));
    serverFormPanel.add(hostNameLabel,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(68, 23), null, 0, false));
    hostName = new JTextField();
    serverFormPanel.add(hostName, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, 23), null, 0, false));
    compress = new JCheckBox();
    this.$$$loadButtonText$$$(compress,
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("compress.with.blah"));
    serverFormPanel.add(compress, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
        null, 0, false));
    encrypt = new JCheckBox();
    this.$$$loadButtonText$$$(encrypt,
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("encrypt.with.blah"));
    serverFormPanel.add(encrypt, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
        null, 0, false));
    context = new JTextField();
    serverFormPanel.add(context, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    contextLabel = new JLabel();
    this.$$$loadLabelText$$$(contextLabel,
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("context"));
    serverFormPanel.add(contextLabel,
        new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    serverStatusPanel = new JPanel();
    serverStatusPanel.setLayout(new GridLayoutManager(1, 1, new Insets(3, 3, 3, 3), -1, -1));
    serverStatusPanel.setEnabled(true);
    serverTab.add(serverStatusPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    serverStatusPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(),
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("server.status")));
    serverStatusMessage = new JTextPane();
    serverStatusMessage.setBackground(UIManager.getColor("Panel.background"));
    serverStatusMessage
        .setText(ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("test.not.performed.yet"));
    serverStatusPanel.add(serverStatusMessage,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, -1), null, 0,
            false));
    testButtonPanel = new JPanel();
    testButtonPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    serverTab.add(testButtonPanel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    testButton = new JButton();
    this.$$$loadButtonText$$$(testButton,
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("test.settings"));
    testButtonPanel.add(testButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer2 = new Spacer();
    testButtonPanel.add(spacer2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    loginTab = new JPanel();
    loginTab.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1));
    prefsTabs.addTab(ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("login"), loginTab);
    loginPanel = new JPanel();
    loginPanel.setLayout(new GridLayoutManager(5, 2, new Insets(0, 0, 0, 0), -1, -1));
    loginTab.add(loginPanel, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    userNameLabel = new JLabel();
    userNameLabel.setHorizontalAlignment(11);
    userNameLabel.setHorizontalTextPosition(0);
    this.$$$loadLabelText$$$(userNameLabel,
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("username"));
    loginPanel.add(userNameLabel,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    userName = new JTextField();
    loginPanel.add(userName, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    passwordLabel = new JLabel();
    passwordLabel.setHorizontalAlignment(11);
    this.$$$loadLabelText$$$(passwordLabel,
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("password"));
    loginPanel.add(passwordLabel,
        new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    password = new JPasswordField();
    loginPanel.add(password, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    rememberPasswordCheckBox = new JCheckBox();
    this.$$$loadButtonText$$$(rememberPasswordCheckBox,
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("remember.password"));
    loginPanel.add(rememberPasswordCheckBox, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
        null, 0, false));
    sessionStateLabel = new JLabel();
    sessionStateLabel.setHorizontalAlignment(11);
    this.$$$loadLabelText$$$(sessionStateLabel,
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("session.state"));
    loginPanel.add(sessionStateLabel,
        new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    sessionState = new JLabel();
    this.$$$loadLabelText$$$(sessionState,
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("unknown"));
    loginPanel.add(sessionState,
        new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    logInButton = new JButton();
    this.$$$loadButtonText$$$(logInButton, ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("log.in"));
    loginPanel.add(logInButton, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
        null, 0, false));
    final Spacer spacer3 = new Spacer();
    loginTab.add(spacer3, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
        GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final Spacer spacer4 = new Spacer();
    loginTab.add(spacer4, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
        GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final Spacer spacer5 = new Spacer();
    loginTab.add(spacer5, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    final Spacer spacer6 = new Spacer();
    loginTab.add(spacer6, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
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

  public enum State {
    good, neutral, bad
  }

  public static Preferences getInstance() {
    if (singleton == null) singleton = new Preferences();
    return singleton;
  }

  private Preferences() {

    setIconImages(ImageResource.getFrameList());
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

        if (serverSettingsOk && loginSettingsOk) setVisible(false);
        else {
          JOptionPane.showMessageDialog((Component)e.getSource(), Strings.get("error.settings.are.not.valid"), Strings.get("error"),
              JOptionPane.ERROR_MESSAGE);
          if (!loginSettingsOk && serverSettingsOk) selectTab(Strings.get("login"));
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
        if (areServerSettingsOk() && areLoginSettingsOk()) setVisible(false);
        else System.exit(0);
      }
    });
    logInButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setSessionState(State.neutral, Strings.get("pending"));
        if (areServerSettingsOk()) areLoginSettingsOk();
        else {
          setSessionState(State.bad, Strings.get("error.server.settings.not.valid"));
        }
      }
    });

    compress.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ssData.setCompress(compress.isSelected());
      }
    });
    encrypt.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ssData.setEncrypt(encrypt.isSelected());
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
    if (prefsTabs.getSelectedIndex() != index) prefsTabs.setSelectedIndex(index);
  }

  public Boolean areLoginSettingsOk() {
    SecurityPortal sp = new SecurityPortal();

    if (!hadLoginSuccessYet && !lsData.passwordIsSaved() && Arrays.equals(password.getPassword(), new char[0])) {
      setSessionState(State.bad, Strings.get("session.state.not.attempted"));
      return false;
    }

    Boolean authed = null;
//    try {
    lsData.setUsername(userName.getText());
    try {
      lsData.setPassword(String.valueOf(password.getPassword()));
      lsData.setPasswordShouldBeSaved(rememberPasswordCheckBox.isSelected());
      Challenge c = sp.getChallenge();
      if (c != null) authed = sp.auth(lsData.challengeResponse(c));

    } catch (BadPaddingException e) {
      e.printStackTrace();
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    } catch (IllegalBlockSizeException e) {
      e.printStackTrace();
    } catch (InvalidKeyException e) {
      e.printStackTrace();
    } catch (NoSuchPaddingException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (AbortException e) {
      e.printStackTrace();
    }

    if (authed != null && authed) {
      hadLoginSuccessYet = true;
      setSessionState(State.good, Strings.get("session.state.authorized"));
    }

    return authed == null ? false : authed;
  }

  public boolean areServerSettingsOk() {
    // set the host/port
    ssData.setHostName(hostName.getText());
    ssData.setContext(context.getText());

    String classTestResult = ssData.testSocketServer();
    if (classTestResult.equals(Strings.get("ok"))) {
      serverStatusMessage.setForeground(Colors.getColor("green"));
      classServerValid = true;
    }
    else {
      // default session status to invalid while testing class server
      setSessionState(State.neutral, Strings.get("pending"));
      serverStatusMessage.setForeground(Colors.getColor("red"));
      classServerValid = false;
    }
    serverStatusMessage.setText(classTestResult);

    if (classServerValid) {
      cancelButton.setEnabled(false);
      return true;
    }
    else {
      cancelButton.setEnabled(true);
      return false;
    }
  }

  public void setSessionState(final State state, final String message) {
    switch (state) {
      case good:
        sessionState.setForeground(Colors.getColor("green"));
        break;
      case neutral:
        sessionState.setForeground(Colors.getColor("gray"));
        break;
      case bad:
        sessionState.setForeground(Colors.getColor("red"));
        break;
    }
    sessionState.setText(message);
  }


  public void fillServerFormWithSavedData() {
    if (System.getProperty("codebase") != null) hostName.setText(System.getProperty("codebase"));
    else hostName.setText(ssData.getHostName() + ":" + ssData.getPort());

    if (System.getProperty("context") != null) context.setText(System.getProperty("context"));
    else context.setText(ssData.getContext());

    compress.setSelected(ssData.getCompress());
    encrypt.setSelected(ssData.getEncrypt());
  }

  public void fillUserFormWithSavedData() {
    userName.setText(lsData.getUsername());
    password.setText(lsData.getPassword());
    rememberPasswordCheckBox.setSelected(lsData.shouldPasswordBeSaved());
  }

  public boolean isModified(ServerSettings data) {
    if (hostName.getText() != null ? !hostName.getText().equals(data.getHostName()) : data.getHostName() != null) return true;
    return false;
  }

}
