package us.lump.envelope.client.ui.components.forms;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.toedter.calendar.JDateChooser;
import com.toedter.calendar.JTextFieldDateEditor;
import us.lump.envelope.client.State;
import us.lump.envelope.client.portal.HibernatePortal;
import us.lump.envelope.client.thread.StatusRunnable;
import us.lump.envelope.client.thread.ThreadPool;
import us.lump.envelope.client.ui.MainFrame;
import us.lump.envelope.client.ui.components.MoneyTextField;
import us.lump.envelope.client.ui.components.models.AllocationFormTableModel;
import us.lump.envelope.client.ui.components.models.TransactionTableModel;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.entity.Transaction;
import us.lump.envelope.exception.AbortException;
import us.lump.lib.Money;

import javax.persistence.Column;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Date;
import java.util.ResourceBundle;

/**
 * A Transaction Form.
 *
 * @author Troy Bowman
 * @version $Id: TransactionForm.java,v 1.16 2009/05/30 20:51:38 troy Exp $
 */
public class TransactionForm {
  private JButton saveButton;
  private JTable allocationsTable;
  private JRadioButton typeExpenseRadio;
  private JRadioButton typeIncomeRadio;
  private JComboBox entity;
  private JTextField description;
  private JComboBox incomeType;
  private JCheckBox saveEachAllocationChangeCheckBox;
  private JTable totalsTable;
  private JPanel transactionFormPanel;
  private JPanel splitpaneAndButtonsPanel;
  private JSplitPane transactionAllocationSplit;
  private JPanel transactionInfoPanel;
  private JLabel dateLabel;
  private JLabel entityLabel;
  private JLabel descriptionLabel;
  private JLabel amountLabel;
  private JLabel typeLabel;
  private JPanel allocationSettingsPanel;
  private JPanel totalsPanel;
  private JPanel allocationsPanel;
  private JScrollPane allocationsScrollPane;
  private JScrollPane totalsScrollPane;
  private JLabel incomeTypeLabel;
  private JLabel referencePaydateLabel;
  private JDateChooser referencePaydate;
  private JDateChooser transactionDate;
  private MoneyTextField amount;
  private JButton newButton;
  private JButton closeButton;

  private AllocationFormTableModel tableModel;

  private Transaction transaction;

  public TransactionForm() {
    $$$setupUI$$$();

    tableModel = new AllocationFormTableModel();
    allocationsTable.setModel(tableModel);
    transactionAllocationSplit.setResizeWeight(0.5);
    transactionAllocationSplit.getLeftComponent().setMinimumSize(new Dimension(200, 0));
    transactionAllocationSplit.getRightComponent().setMinimumSize(new Dimension(200, 0));
    transactionAllocationSplit.setContinuousLayout(true);
    transactionAllocationSplit.setOneTouchExpandable(false);

    entity.setFont(entity.getFont().deriveFont(Font.PLAIN));

    ButtonGroup transactionTypeButtonGroup = new ButtonGroup();
    transactionTypeButtonGroup.add(typeExpenseRadio);
    transactionTypeButtonGroup.add(typeIncomeRadio);

    Action expenseRadioAction = new AbstractAction(Strings.get("expense")) {
      public void actionPerformed(ActionEvent e) {
        setExpenseView();
      }
    };
    Action incomeRadioAction = new AbstractAction(Strings.get("income")) {
      public void actionPerformed(ActionEvent e) {
        setIncomeView();
      }
    };

    typeExpenseRadio.setFont(typeExpenseRadio.getFont().deriveFont(Font.PLAIN));
    typeIncomeRadio.setFont(typeExpenseRadio.getFont().deriveFont(Font.PLAIN));

    typeExpenseRadio.setAction(expenseRadioAction);
    typeIncomeRadio.setAction(incomeRadioAction);

    transactionTypeButtonGroup.setSelected(typeExpenseRadio.getModel(), true);
    typeExpenseRadio.doClick();

    saveButton.setDefaultCapable(true);

//    transactionDate.setInputVerifier(new InputVerifier(){
//      public boolean verify(JComponent input) {
//        if (input instanceof JDateChooser) {
//          JDateChooser i = (JDateChooser)input;
//          try {
//            i.
//            Date date = new Date(getText());
//
//          i.setText(new Money(i.getText()).toFormattedString());
//            i.setBackground(Colors.getColor("light_green"));
//            return true;
//          }
//          catch (Exception e) {
//            i.setBackground(Colors.getColor("light_red"));
//            return false;
//          }
//        }
//      }
//    }
//    splitpaneAndButtonsPanel.setMinimumSize(new Dimension(0, 300));
    splitpaneAndButtonsPanel.setMinimumSize(new Dimension(0, 300));


    final JTable table = TableQueryBar.getInstance().getTable();
    table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        if (table.getSelectedRow() < 0) return;
        loadTransactionForId(((TransactionTableModel)table.getModel()).getTransactionId(table.getSelectedRow()));
      }
    });

    table.addMouseListener(new MouseListener() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          MainFrame.getInstance().setTransactionViewShowing(true);
          loadTransactionForId(((TransactionTableModel)table.getModel()).getTransactionId(table.getSelectedRow()));

        }
      }

      public void mousePressed(MouseEvent e) {}

      public void mouseReleased(MouseEvent e) {}

      public void mouseEntered(MouseEvent e) {}

      public void mouseExited(MouseEvent e) {}
    });

    closeButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        MainFrame.getInstance().setTransactionViewShowing(false);
        MainFrame.getInstance().doViewTransaction();
      }
    });
  }

  public void setIncomeView() {
    allocationSettingsPanel.setVisible(true);
    typeExpenseRadio.setSelected(false);
    typeIncomeRadio.setSelected(true);
    entityLabel.setText(Strings.get("received.from"));
    amountLabel.setText(Strings.get("gross.amount"));
  }

  public void setExpenseView() {
    allocationSettingsPanel.setVisible(false);
    typeExpenseRadio.setSelected(true);
    typeIncomeRadio.setSelected(false);
    entityLabel.setText(Strings.get("paid.to"));
    amountLabel.setText(Strings.get("total.amount"));
  }

  public void loadTransactionForId(final int id) {
    if (!MainFrame.getInstance().isTransactionViewShowing()) return;
    if (transaction == null || !transaction.getId().equals(id)) {
      StatusRunnable r = new StatusRunnable(MessageFormat.format(Strings.get("retrieving.transaction"), id)) {
        public void run() {

          try {
            transaction = new HibernatePortal().get(Transaction.class, id);

            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                try {
                  if (transaction.getAmount().doubleValue() > 0) {
                    setIncomeView();
                  }
                  else {
                    setExpenseView();
                  }

                  amount.setText(typeExpenseRadio.isSelected() ? new Money(transaction.getAmount().multiply(new Money("-1")))
                      .toFormattedString() : transaction.getAmount().toFormattedString());

                  transactionDate.setDate(transaction.getDate());

                  try {
                    description.setDocument(new LimitDocument(Transaction.class.getMethod("getDescription")));
                  } catch (NoSuchMethodException ignore) {}

                  description.setText(transaction.getDescription());

                  refreshEntities();


                  try {
                    ((JTextField)entity.getEditor().getEditorComponent())
                        .setDocument(new LimitDocument(Transaction.class.getMethod("getEntity")));
                  } catch (NoSuchMethodException ignore) {}

                  entity.setSelectedItem(transaction.getEntity());

                  tableModel.setAllocations(transaction.getAllocations());
                  System.out.println(transaction);
                } catch (AbortException ignore) {}
              }
            });
          } catch (AbortException ignore) {}

        }
      };
      ThreadPool.getInstance().execute(r);
    }
  }

  public synchronized void refreshEntities() throws AbortException {
    entity.removeAllItems();
    for (String e : State.getInstance().entities()) {
      entity.addItem(e);
    }
  }

  public JPanel getTransactionFormPanel() {
    return transactionFormPanel;
  }

  /** Method generated by IntelliJ IDEA GUI Designer >>> IMPORTANT!! <<< DO NOT edit this method OR call it in your code! */
  private void $$$setupUI$$$() {
    createUIComponents();
    transactionFormPanel = new JPanel();
    transactionFormPanel.setLayout(new GridLayoutManager(2, 4, new Insets(0, 0, 0, 0), -1, -1));
    final JScrollPane scrollPane1 = new JScrollPane();
    transactionFormPanel.add(scrollPane1, new GridConstraints(0, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    splitpaneAndButtonsPanel = new JPanel();
    splitpaneAndButtonsPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    scrollPane1.setViewportView(splitpaneAndButtonsPanel);
    transactionAllocationSplit = new JSplitPane();
    splitpaneAndButtonsPanel.add(transactionAllocationSplit,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0,
            false));
    transactionInfoPanel = new JPanel();
    transactionInfoPanel.setLayout(new GridLayoutManager(8, 2, new Insets(0, 0, 0, 0), -1, -1));
    transactionAllocationSplit.setLeftComponent(transactionInfoPanel);
    transactionInfoPanel.setBorder(BorderFactory.createTitledBorder(
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("transaction")));
    dateLabel = new JLabel();
    this.$$$loadLabelText$$$(dateLabel, ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("date"));
    transactionInfoPanel.add(dateLabel,
        new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    entityLabel = new JLabel();
    this.$$$loadLabelText$$$(entityLabel, ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("paid.to"));
    transactionInfoPanel.add(entityLabel,
        new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    entity = new JComboBox();
    entity.setEditable(true);
    transactionInfoPanel.add(entity, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
        null, 0, false));
    descriptionLabel = new JLabel();
    this.$$$loadLabelText$$$(descriptionLabel,
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("description"));
    transactionInfoPanel.add(descriptionLabel,
        new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    description = new JTextField();
    transactionInfoPanel.add(description,
        new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
            new Dimension(150, -1), null, 0, false));
    amountLabel = new JLabel();
    amountLabel.setText("Amount");
    transactionInfoPanel.add(amountLabel,
        new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    typeExpenseRadio = new JRadioButton();
    this.$$$loadButtonText$$$(typeExpenseRadio,
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("expense"));
    transactionInfoPanel.add(typeExpenseRadio,
        new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
            null, null, 0, false));
    allocationSettingsPanel = new JPanel();
    allocationSettingsPanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
    transactionInfoPanel.add(allocationSettingsPanel,
        new GridConstraints(6, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    allocationSettingsPanel.setBorder(BorderFactory.createTitledBorder("Allocation Settings"));
    incomeType = new JComboBox();
    allocationSettingsPanel.add(incomeType,
        new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
            null, null, 0, false));
    allocationSettingsPanel.add(referencePaydate,
        new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
    incomeTypeLabel = new JLabel();
    this.$$$loadLabelText$$$(incomeTypeLabel,
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("income.type"));
    allocationSettingsPanel.add(incomeTypeLabel,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    referencePaydateLabel = new JLabel();
    this.$$$loadLabelText$$$(referencePaydateLabel,
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("reference.paydate"));
    allocationSettingsPanel.add(referencePaydateLabel,
        new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    saveEachAllocationChangeCheckBox = new JCheckBox();
    this.$$$loadButtonText$$$(saveEachAllocationChangeCheckBox,
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("save.allocation.changes"));
    allocationSettingsPanel.add(saveEachAllocationChangeCheckBox,
        new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
            null, null, 0, false));
    totalsPanel = new JPanel();
    totalsPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    transactionInfoPanel.add(totalsPanel, new GridConstraints(7, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    totalsPanel.setBorder(
        BorderFactory.createTitledBorder(ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("totals")));
    totalsScrollPane = new JScrollPane();
    totalsPanel.add(totalsScrollPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    totalsTable = new JTable();
    totalsScrollPane.setViewportView(totalsTable);
    amount = new MoneyTextField();
    amount.setColumns(15);
    transactionInfoPanel.add(amount, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    transactionInfoPanel.add(transactionDate,
        new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    typeIncomeRadio = new JRadioButton();
    this.$$$loadButtonText$$$(typeIncomeRadio,
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("expense"));
    transactionInfoPanel.add(typeIncomeRadio,
        new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
            null, null, 0, false));
    typeLabel = new JLabel();
    typeLabel.setText("Type");
    transactionInfoPanel.add(typeLabel,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    allocationsPanel = new JPanel();
    allocationsPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    transactionAllocationSplit.setRightComponent(allocationsPanel);
    allocationsPanel.setBorder(BorderFactory.createTitledBorder(
        ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("allocations")));
    allocationsScrollPane = new JScrollPane();
    allocationsPanel.add(allocationsScrollPane,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    allocationsTable = new JTable();
    allocationsScrollPane.setViewportView(allocationsTable);
    final Spacer spacer1 = new Spacer();
    transactionFormPanel.add(spacer1,
        new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    saveButton = new JButton();
    saveButton.setEnabled(false);
    saveButton.setText("Save");
    transactionFormPanel.add(saveButton,
        new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
            null, null, 0, false));
    newButton = new JButton();
    newButton.setEnabled(false);
    this.$$$loadButtonText$$$(newButton, ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("new"));
    transactionFormPanel.add(newButton,
        new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
            null, null, 0, false));
    closeButton = new JButton();
    this.$$$loadButtonText$$$(closeButton, ResourceBundle.getBundle("us/lump/envelope/client/ui/defs/Strings").getString("close"));
    transactionFormPanel.add(closeButton,
        new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
            null, null, 0, false));
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
  public JComponent $$$getRootComponent$$$() { return transactionFormPanel; }

  class LimitDocument extends PlainDocument {
    Method method;

    public LimitDocument(Method method) {
      this.method = method;
    }

    public void insertString(int offset, String str, AttributeSet attr) throws BadLocationException {
      if (str == null) return;
      if (method.isAnnotationPresent(Column.class)) {
        if ((getLength() + str.length()) <= method.getAnnotation(Column.class).length()) {
          super.insertString(offset, str, attr);
        }
      }
    }
  }

  private void createUIComponents() {
    Long today = System.currentTimeMillis();
    today = today - (today % 86400000);

    transactionDate = new JDateChooser(new Date(today), "MMM d, yyyy", new JTextFieldDateEditor());

    transactionDate
        .setPreferredSize(new Dimension(transactionDate.getPreferredSize().width + 30, transactionDate.getPreferredSize().height));

    referencePaydate = new JDateChooser(null, "MMM d, yyyy", new JTextFieldDateEditor());

  }

}
