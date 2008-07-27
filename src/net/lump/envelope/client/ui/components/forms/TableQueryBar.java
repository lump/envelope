package us.lump.envelope.client.ui.components.forms;

import com.toedter.calendar.JDateChooser;
import com.toedter.calendar.JTextFieldDateEditor;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.Spacer;

import javax.swing.*;
import java.awt.*;
import java.util.ResourceBundle;
import java.util.Date;

/**
 * Created by IntelliJ IDEA. User: troy Date: Jul 7, 2008 Time: 10:39:36 PM To
 * change this template use File | Settings | File Templates.
 */
public class TableQueryBar {
  private JPanel queryPanel;
  private JDateChooser beginDate;
  private JLabel endDateLabel;
  private JDateChooser endDate;
  private JButton refreshButton;
  private JScrollPane tableScrollPane;
  private JLabel titleLabel;
  private JPanel tableQueryPanel;

  private static TableQueryBar singleton;

  public TableQueryBar() {
    $$$setupUI$$$();
  }

  public static TableQueryBar getInstance() {
    if (singleton == null) singleton = new TableQueryBar();
    return singleton;
  }

  public JPanel getTableQueryPanel() {
    return tableQueryPanel;
  }

  public void setViewportView(Component c) {
    tableScrollPane.setViewportView(c);
  }

  public Date getBeginDate() {
    return beginDate.getDate();
  }

  public void setBeginDate(Date set) {
    beginDate.setDate(set);
  }

  public Date getEndDate() {
    return endDate.getDate();
  }

  public void setEndDate(Date set) {
    endDate.setDate(set);
  }

  public JButton getRefreshButton() {
    return refreshButton;
  }

  public void setTitleLabel(String title) {
    titleLabel.setText(title);
  }


  private void createUIComponents() {
    Long today = System.currentTimeMillis();
    today = ((today - (today % 86400000)) + 86400000);
    beginDate = new JDateChooser(new Date(today - (86400000L * 90)),
                                 "MMM d, yyyy",
                                 new JTextFieldDateEditor());
    endDate = new JDateChooser(new Date(today),
                               "MMM d, yyyy",
                               new JTextFieldDateEditor());
    beginDate.setPreferredSize(new Dimension(beginDate.getPreferredSize()
        .width
                                             + 10,
                                             beginDate.getPreferredSize().height));
    endDate.setPreferredSize(new Dimension(endDate.getPreferredSize()
        .width
                                           + 10,
                                           endDate.getPreferredSize().height));
  }

  /**
   * Method generated by IntelliJ IDEA GUI Designer >>> IMPORTANT!! <<< DO NOT
   * edit this method OR call it in your code!
   */
  private void $$$setupUI$$$() {
    createUIComponents();
    tableQueryPanel = new JPanel();
    tableQueryPanel.setLayout(new BorderLayout(0, 0));
    queryPanel = new JPanel();
    queryPanel.setLayout(new GridLayoutManager(1,
                                               6,
                                               new Insets(0, 2, 0, 2),
                                               2,
                                               2));
    tableQueryPanel.add(queryPanel, BorderLayout.NORTH);
    queryPanel.add(beginDate, new GridConstraints(0,
                                                  2,
                                                  1,
                                                  1,
                                                  GridConstraints.ANCHOR_CENTER,
                                                  GridConstraints.FILL_NONE,
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
    endDateLabel = new JLabel();
    endDateLabel.setHorizontalAlignment(4);
    endDateLabel.setHorizontalTextPosition(4);
    this.$$$loadLabelText$$$(endDateLabel, ResourceBundle.getBundle(
        "us/lump/envelope/client/ui/defs/Strings").getString("to"));
    queryPanel.add(endDateLabel, new GridConstraints(0,
                                                     3,
                                                     1,
                                                     1,
                                                     GridConstraints.ANCHOR_CENTER,
                                                     GridConstraints.FILL_NONE,
                                                     1,
                                                     1,
                                                     null,
                                                     null,
                                                     null,
                                                     0,
                                                     false));
    queryPanel.add(endDate, new GridConstraints(0,
                                                4,
                                                1,
                                                1,
                                                GridConstraints.ANCHOR_CENTER,
                                                GridConstraints.FILL_NONE,
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
    refreshButton = new JButton();
    this.$$$loadButtonText$$$(refreshButton, ResourceBundle.getBundle(
        "us/lump/envelope/client/ui/defs/Strings").getString("refresh"));
    queryPanel.add(refreshButton, new GridConstraints(0,
                                                      5,
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
    titleLabel = new JLabel();
    titleLabel.setFont(new Font("Dialog", Font.BOLD, 16));
    titleLabel.setText("");
    queryPanel.add(titleLabel, new GridConstraints(0,
                                                   0,
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
    final Spacer spacer1 = new Spacer();
    queryPanel.add(spacer1, new GridConstraints(0,
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
    tableScrollPane = new JScrollPane();
    tableQueryPanel.add(tableScrollPane, BorderLayout.CENTER);
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
  public JComponent $$$getRootComponent$$$() { return tableQueryPanel; }
}
