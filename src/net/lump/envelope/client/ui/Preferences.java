package us.lump.envelope.client.ui;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import us.lump.envelope.client.ui.defs.Colors;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.client.ui.prefs.ServerSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ResourceBundle;

public class Preferences extends JDialog {
  private JPanel prefsPane;
  private JButton ok;
  private JTabbedPane prefsTabs;
  private JTextField hostName;
  private JTextField rmiPort;
  private JTextField classPort;
  private JPanel okCancelGroup;
  private JPanel okCancelPanel;
  private JPanel tabPanel;
  private JLabel hostNameLabel;
  private JLabel classPortLabel;
  private JLabel rmiPortLabel;
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
  private JTextField textField1;
  private JPasswordField passwordField1;
  private JCheckBox rememberPasswordCheckBox;
  private JButton logInButton;
  private ServerSettings ssData = ServerSettings.getInstance();
  private Boolean classServerValid = null;
  private Boolean rmiServerValid = null;

  public Preferences() {

    setContentPane(prefsPane);
    setModal(true);
    getRootPane().setDefaultButton(ok);
    setData(ssData);
    testButton.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        super.mousePressed(e);
        // reset the cache when we're explicitly checking
        ServerSettings.getInstance().resetCache();
        areServerSettingsOk();
      }
    });

    ok.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        getData(ssData);
        if (areServerSettingsOk())
          setVisible(false);
        else {
          JOptionPane.showMessageDialog(
              (Component)e.getSource(),
              Strings.get("error.settings_are_not_valid"),
              Strings.get("error"),
              JOptionPane.ERROR_MESSAGE);
        }
      }
    });
    classPort.addKeyListener(new KeyAdapter() {
      public void keyTyped(KeyEvent e) {
        super.keyTyped(e);

        if (String.valueOf(e.getKeyChar()).matches("^\\d$")) {
        }
      }
    });
    cancelButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (areServerSettingsOk())
          setVisible(false);
        else
          System.exit(0);
      }
    });
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

  public boolean areServerSettingsOk() {
    if (isClassServerValid() && isRmiServerValid()) {
      cancelButton.setEnabled(false);
      return true;
    } else {
      cancelButton.setEnabled(true);
      return false;
    }
  }


  public boolean isClassServerValid() {
    getData(ssData);
    String classTestResult = ssData.testClassServer();
    if (classTestResult.equals(Strings.get("ok"))) {
      classStatusMessage.setForeground(Colors.getColor("green"));
      classServerValid = true;
    } else {
      classStatusMessage.setForeground(Colors.getColor("red"));
      classServerValid = false;
    }
    classStatusMessage.setText(classTestResult);
    return classServerValid;
  }

  public boolean isRmiServerValid() {
    getData(ssData);
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

  public void setData(ServerSettings data) {
    hostName.setText(data.getHostName());
    rmiPort.setText(data.getRmiPort());
    classPort.setText(data.getClassPort());
  }

  public void getData(ServerSettings data) {
    data.setHostName(hostName.getText());
    data.setRmiPort(rmiPort.getText());
    data.setClassPort(classPort.getText());
  }

  public boolean isModified(ServerSettings data) {
    if (hostName.getText() != null ? !hostName.getText().equals(data.getHostName()) : data.getHostName() != null)
      return true;
    if (rmiPort.getText() != null ? !rmiPort.getText().equals(data.getRmiPort()) : data.getRmiPort() != null)
      return true;
    if (classPort.getText() != null ? !classPort.getText().equals(data.getClassPort()) : data.getClassPort() != null)
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
   * Method generated by IntelliJ IDEA GUI Designer
   * >>> IMPORTANT!! <<<
   * DO NOT edit this method OR call it in your code!
   *
   * @noinspection ALL
   */
  private void $$$setupUI$$$() {
    prefsPane = new JPanel();
    prefsPane.setLayout(new GridLayoutManager(2, 1, new Insets(10, 10, 10, 10), -1, -1));
    okCancelPanel = new JPanel();
    okCancelPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    prefsPane.add(okCancelPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    okCancelPanel.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    okCancelGroup = new JPanel();
    okCancelGroup.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    okCancelPanel.add(okCancelGroup, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    ok = new JButton();
    this.$$$loadButtonText$$$(ok, ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("ok"));
    okCancelGroup.add(ok, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    cancelButton = new JButton();
    this.$$$loadButtonText$$$(cancelButton, ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("cancel"));
    okCancelPanel.add(cancelButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    tabPanel = new JPanel();
    tabPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    prefsPane.add(tabPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    prefsTabs = new JTabbedPane();
    tabPanel.add(prefsTabs, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(4, 1, new Insets(5, 5, 5, 5), -1, -1));
    prefsTabs.addTab(ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("server"), panel1);
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
    panel1.add(panel2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    hostNameLabel = new JLabel();
    this.$$$loadLabelText$$$(hostNameLabel, ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("hostname"));
    panel2.add(hostNameLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    classPortLabel = new JLabel();
    this.$$$loadLabelText$$$(classPortLabel, ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("class_port"));
    panel2.add(classPortLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    hostName = new JTextField();
    panel2.add(hostName, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    classPort = new JTextField();
    panel2.add(classPort, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    rmiPortLabel = new JLabel();
    this.$$$loadLabelText$$$(rmiPortLabel, ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("rmi_port"));
    panel2.add(rmiPortLabel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    rmiPort = new JTextField();
    panel2.add(rmiPort, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    final JPanel panel3 = new JPanel();
    panel3.setLayout(new GridLayoutManager(1, 1, new Insets(3, 3, 3, 3), -1, -1));
    panel1.add(panel3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    panel3.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("class_server_status")));
    classStatusMessage = new JTextPane();
    classStatusMessage.setBackground(UIManager.getColor("Label.background"));
    classStatusMessage.setEditable(false);
    classStatusMessage.setForeground(UIManager.getColor("Label.foreground"));
    classStatusMessage.setText(ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("test_not_performed_yet"));
    panel3.add(classStatusMessage, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, -1), null, 0, false));
    final JPanel panel4 = new JPanel();
    panel4.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    panel1.add(panel4, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    panel4.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("rmi_server_status")));
    rmiStatusMessage = new JTextPane();
    rmiStatusMessage.setBackground(UIManager.getColor("Label.background"));
    rmiStatusMessage.setForeground(UIManager.getColor("Label.foreground"));
    rmiStatusMessage.setText(ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("test_not_performed_yet"));
    panel4.add(rmiStatusMessage, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, -1), null, 0, false));
    final JPanel panel5 = new JPanel();
    panel5.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    panel1.add(panel5, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    testButton = new JButton();
    this.$$$loadButtonText$$$(testButton, ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("test_settings"));
    panel5.add(testButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer2 = new Spacer();
    panel5.add(spacer2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
  }

  /**
   * @noinspection ALL
   */
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

  /**
   * @noinspection ALL
   */
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

  /**
   * @noinspection ALL
   */
  public JComponent $$$getRootComponent$$$() { return prefsPane; }
}
