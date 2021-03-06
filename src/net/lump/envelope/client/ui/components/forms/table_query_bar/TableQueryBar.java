package net.lump.envelope.client.ui.components.forms.table_query_bar;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.toedter.calendar.JDateChooser;
import com.toedter.calendar.JTextFieldDateEditor;
import net.lump.envelope.client.ui.components.TransactionTableModel;
import net.lump.envelope.client.ui.defs.Strings;
import net.lump.envelope.client.ui.images.ImageResource;
import net.lump.lib.Money;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Date;
import java.util.ResourceBundle;

public class TableQueryBar {
  private JPanel queryPanel;
  private JDateChooser beginDate;
  private JLabel endDateLabel;
  private JDateChooser endDate;
  private JButton refreshButton;
  private JScrollPane tableScrollPane;
  private JLabel titleLabel;
  private JPanel tableQueryPanel;
  private JTable table;
  private JLabel outBoxLabel;
  private JLabel inBoxLabel;

  private static TableQueryBar singleton;

  public TableQueryBar() {

    $$$setupUI$$$();

    inBoxLabel.setIcon(ImageResource.icon.inbox.get());
    inBoxLabel.setToolTipText(Strings.get("incoming"));
    setInboxLabel(Money.ZERO.toString());
    outBoxLabel.setIcon(ImageResource.icon.outbox.get());
    outBoxLabel.setToolTipText(Strings.get("outgoing"));
    setOutboxLabel(Money.ZERO.toString());

    table.getTableHeader().setUpdateTableInRealTime(true);
    tableQueryPanel.setMinimumSize(new Dimension(500, 100));
    table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    table.getTableHeader().setReorderingAllowed(false);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    table.addKeyListener(new KeyListener() {
      public void keyTyped(KeyEvent e) {
//        if (e.getKeyChar() == ' ' && e.getModifiersEx() == 0) {
//          toggleAndMoveOn(true);
//        }
//        else if (e.getKeyChar() == ' '
//          && ((e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) == KeyEvent.SHIFT_DOWN_MASK)) {
//          toggleAndMoveOn(false);
//        }
      }

      public void keyPressed(KeyEvent e) {
      }

      public void keyReleased(KeyEvent e) {
        if (((TransactionTableModel)table.getModel()).isTransaction()) {
          if (e.getKeyCode() == KeyEvent.VK_SPACE
              && (e.getModifiersEx() == 0)) {
            toggleAndMoveOn(true);
          }
          if (e.getKeyCode() == KeyEvent.VK_SPACE
              && ((e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) == KeyEvent.SHIFT_DOWN_MASK)) {
            toggleAndMoveOn(false);
          }
        }
      }

      private void toggleAndMoveOn(boolean down) {
        boolean value = (Boolean)table.getValueAt(table.getSelectedRow(), 0);
        table.setValueAt(!value, table.getSelectedRow(), 0);

        if (down) {
          if ((table.getSelectedRow() + 1) < table.getRowCount())
            table.changeSelection(table.getSelectedRow() + 1, table.getSelectedColumn(), false, false);
          while ((table.getSelectedRow() + 1) < table.getRowCount() && !(table.getValueAt(table.getSelectedRow(), 0)).equals(value))
            table.changeSelection(table.getSelectedRow() + 1, table.getSelectedColumn(), false, false);
        }
        else {
          if ((table.getSelectedRow() - 1) >= 0)
            table.changeSelection(table.getSelectedRow() - 1, table.getSelectedColumn(), false, false);
          while ((table.getSelectedRow() - 1) >= 0 && !(table.getValueAt(table.getSelectedRow(), 0)).equals(value))
            table.changeSelection(table.getSelectedRow() - 1, table.getSelectedColumn(), false, false);
        }
      }
    });

    KeyListener refreshKeyListener = new KeyListener() {

      public void keyTyped(KeyEvent e) {}

      public void keyReleased(KeyEvent e) {}

      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          for (ActionListener al : refreshButton.getActionListeners()) {
            al.actionPerformed(new ActionEvent(e.getSource(), e.getID(), refreshButton.getText()));
          }
        }
      }
    };

    beginDate.getDateEditor().getUiComponent().addKeyListener(refreshKeyListener);
    endDate.getDateEditor().getUiComponent().addKeyListener(refreshKeyListener);

//    table.getSelectionModel().addListSelectionListener(new ListSelectionListener(){
//      public void valueChanged(ListSelectionEvent e) {
//        if (!e.getValueIsAdjusting()) {
//          ((TransactionTableModel)table.getModel()).setSelectionCache(
//              ((DefaultListSelectionModel)e.getSource()).getAnchorSelectionIndex());
//        }
//      }
//    });
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

  public void setTitleIcon(Icon title) {
    titleLabel.setIcon(title);
  }

  public void setInboxLabel(String inboxLabel) {
    inBoxLabel.setText(inboxLabel);
  }

  public void setOutboxLabel(String inboxLabel) {
    outBoxLabel.setText(inboxLabel);
  }

  public JTable getTable() {
    return table;
  }

  private void createUIComponents() {
    Long today = System.currentTimeMillis();
    today = ((today - (today % 86400000)) + 86400000);
    beginDate = new JDateChooser(new Date(today - (86400000L * 90)), "MM/dd/yyyy", new
        JTextFieldDateEditor("MM/dd/yyyy", "##/##/####", '_'));
    //beginDate.setFont(Fonts.fixed.getFont());

    endDate = new JDateChooser(new Date(today), "MM/dd/yyyy", new JTextFieldDateEditor("MM/dd/yyyy", "##/##/####", '_'));
    //endDate.setFont(Fonts.fixed.getFont());
    beginDate.setPreferredSize(new Dimension(beginDate.getPreferredSize().width + 10, beginDate.getPreferredSize().height));
    endDate.setPreferredSize(new Dimension(endDate.getPreferredSize().width + 10, endDate.getPreferredSize().height));
  }

  /** Method generated by IntelliJ IDEA GUI Designer >>> IMPORTANT!! <<< DO NOT edit this method OR call it in your code! */
  private void $$$setupUI$$$() {
    createUIComponents();
    tableQueryPanel = new JPanel();
    tableQueryPanel.setLayout(new BorderLayout(0, 0));
    queryPanel = new JPanel();
    queryPanel.setLayout(new GridLayoutManager(1, 9, new Insets(0, 2, 0, 2), 2, 2));
    tableQueryPanel.add(queryPanel, BorderLayout.NORTH);
    queryPanel.add(beginDate, new GridConstraints(0, 5, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
    endDateLabel = new JLabel();
    endDateLabel.setHorizontalAlignment(4);
    endDateLabel.setHorizontalTextPosition(4);
    this.$$$loadLabelText$$$(endDateLabel, ResourceBundle.getBundle("net/lump/envelope/client/ui/defs/Strings").getString("to"));
    queryPanel.add(endDateLabel,
        new GridConstraints(0, 6, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, 1, 1, null, null, null, 0,
            false));
    queryPanel.add(endDate, new GridConstraints(0, 7, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
    refreshButton = new JButton();
    this.$$$loadButtonText$$$(refreshButton,
        ResourceBundle.getBundle("net/lump/envelope/client/ui/defs/Strings").getString("refresh"));
    queryPanel.add(refreshButton, new GridConstraints(0, 8, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
        null, 0, false));
    titleLabel = new JLabel();
    titleLabel.setFont(new Font("Dialog", Font.BOLD, 16));
    titleLabel.setText("");
    queryPanel.add(titleLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
        null, 0, false));
    final Spacer spacer1 = new Spacer();
    queryPanel.add(spacer1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    outBoxLabel = new JLabel();
    outBoxLabel.setText("");
    queryPanel.add(outBoxLabel,
        new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    inBoxLabel = new JLabel();
    inBoxLabel.setText("");
    queryPanel.add(inBoxLabel,
        new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer2 = new Spacer();
    queryPanel.add(spacer2, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    tableScrollPane = new JScrollPane();
    tableQueryPanel.add(tableScrollPane, BorderLayout.CENTER);
    table = new JTable();
    tableScrollPane.setViewportView(table);
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    tableQueryPanel.add(panel1, BorderLayout.SOUTH);
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
