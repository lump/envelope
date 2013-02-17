package net.lump.envelope.client.ui.components.forms.transaction;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.toedter.calendar.JDateChooser;
import com.toedter.calendar.JTextFieldDateEditor;
import net.lump.envelope.client.CriteriaFactory;
import net.lump.envelope.client.State;
import net.lump.envelope.client.portal.HibernatePortal;
import net.lump.envelope.client.thread.StatusRunnable;
import net.lump.envelope.client.ui.MainFrame;
import net.lump.envelope.client.ui.components.MoneyTextField;
import net.lump.envelope.client.ui.components.StatusBar;
import net.lump.envelope.client.ui.components.TransactionTableModel;
import net.lump.envelope.client.ui.components.forms.table_query_bar.TableQueryBar;
import net.lump.envelope.client.ui.defs.Strings;
import net.lump.envelope.client.ui.images.ImageResource;
import net.lump.envelope.shared.command.OutputEvent;
import net.lump.envelope.shared.command.OutputListener;
import net.lump.envelope.shared.entity.Category;
import net.lump.envelope.shared.entity.Transaction;
import net.lump.envelope.shared.exception.AbortException;
import net.lump.lib.Money;

import javax.persistence.Column;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A Transaction Form.
 *
 * @author Troy Bowman
 * @version $Id: TransactionForm.java,v 1.25 2010/09/22 19:27:36 troy Exp $
 */
public class TransactionForm {
  private JTable allocationsTable;
  private JRadioButton typeExpenseRadio;
  private JRadioButton typeIncomeRadio;
  private JComboBox<String> entity;
  private JTextField description;
  private JPanel transactionFormPanel;
  private JPanel splitpanePanel;
  private JSplitPane transactionAllocationSplit;
  private JPanel transactionInfoPanel;
  private JLabel dateLabel;
  private JLabel entityLabel;
  private JLabel descriptionLabel;
  private JLabel amountLabel;
  private JLabel typeLabel;
  private JPanel allocationsPanel;
  private JScrollPane allocationsScrollPane;
  private JDateChooser transactionDate;
  private MoneyTextField amount;
  private JPanel totalsPanel;
  private JPanel imbalanceMessagePanel;
  private JLabel imbalanceMessageLabel;
  private JLabel inboxLabel;
  private JLabel balanceLabel;
  private JLabel outboxLabel;
  private JPanel saveStatePanel;
  private JLabel saveStateLabel;
  private GridLayout totalsGridLayout;

  private AllocationFormTableModel tableModel;
  private JComboBox categoriesComboBox = new JComboBox();
  private TransactionChangeHandler transactionChangeHandler;

  private BlockingQueue<StatusRunnable> updateQueue = new LinkedBlockingQueue<StatusRunnable>();
  ChangeableDateChooser changeableDateChooser;
  ChangeableJTextField changeableJTextField;

  public TransactionForm() {
    $$$setupUI$$$();

    inboxLabel.setIcon(ImageResource.icon.inbox_16.get());
    inboxLabel.setToolTipText(Strings.get("incoming"));
    setInboxLabel(Money.ZERO.toString());

    balanceLabel.setIcon(ImageResource.icon.balance_16.get());
    balanceLabel.setToolTipText(Strings.get("balance"));
    setBalanceLabel(Money.ZERO.toString());

    outboxLabel.setIcon(ImageResource.icon.outbox_16.get());
    outboxLabel.setToolTipText(Strings.get("outgoing"));
    setOutboxLabel(Money.ZERO.toString());

    tableModel = new AllocationFormTableModel(allocationsTable);
    allocationsTable.setModel(tableModel);
    allocationsTable.setDefaultRenderer(Money.class, new MoneyRenderer());
    allocationsTable.setDefaultRenderer(Category.class, new DefaultTableCellRenderer() {
      public Component getTableCellRendererComponent(JTable table,
          Object value,
          boolean isSelected,
          boolean hasFocus,
          int row,
          int col) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
        if (hasFocus) table.editCellAt(row, col);
        return c;
      }
    });
    allocationsTable.setSurrendersFocusOnKeystroke(false);
    allocationsTable.setRowHeight(allocationsTable.getRowHeight() + 5);
/*
    allocationsTable.addFocusListener(new FocusListener() {
      public void focusGained(FocusEvent e) {}
      public void focusLost(FocusEvent e) {
        if (allocationsTable.isEditing())
          allocationsTable.getCellEditor().stopCellEditing();
      }
    });
    */

    allocationsTable.putClientProperty("terminateEditOnFocusLost", true);

    allocationsTable.addFocusListener(new FocusListener() {
      public void focusGained(FocusEvent e) {
        System.out.println("focus gained " + e.getComponent().getName());
      }

      public void focusLost(FocusEvent e) {
        System.out.println("focus lost " + e.getComponent().getName());
      }
    });

    allocationsTable.addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        System.out.println("allocationtable property change: " + evt.getPropertyName() + " " + evt.getNewValue());
        //To change body of implemented methods use File | Settings | File Templates.
      }
    });

    tableModel.addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        String type = "";
        switch (e.getType()) {
          case TableModelEvent.UPDATE:
            type = "update";
            break;
          case TableModelEvent.INSERT:
            type = "insert";
            break;
          case TableModelEvent.DELETE:
            type = "delete";
            break;
          //case TableModelEvent.ALL_COLUMNS: type = "all columns"; break;
          //case TableModelEvent.HEADER_ROW: type = "header row"; break;
        }
        System.out.println("table event: " + type + " row: " + e.getFirstRow() + " column: " + e.getColumn());

        if (e.getType() == TableModelEvent.UPDATE)
          if (transactionChangeHandler != null)
            transactionChangeHandler.updateAllocationTotalLabels();
      }
    });

    if (!tableModel.hasEmptyRow()) tableModel.addEmptyRow();

//    allocationsTable.addPropertyChangeListener(new PropertyChangeListener() {
//      public void propertyChange(PropertyChangeEvent evt) {
//        System.out.println(evt.getPropertyName());
//        setInboxLabel(tableModel.getIn().toString());
//        setBalanceLabel(tableModel.getBalance().toString());
//        setOutboxLabel(tableModel.getOut().toString());
//      }
//    });

    categoriesComboBox.setLightWeightPopupEnabled(true);
    CellEditor categoryCellEditor = new CellEditor(categoriesComboBox);
    MoneyTextField moneyEditor = new MoneyTextField();
    CellEditor amountCellEditor = new CellEditor(moneyEditor);
    categoryCellEditor.setClickCountToStart(0);
    amountCellEditor.setClickCountToStart(0);
    allocationsTable.setDefaultEditor(Category.class, categoryCellEditor);
    allocationsTable.setDefaultEditor(Money.class, amountCellEditor);

    allocationsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    allocationsTable.setRowSelectionAllowed(false);
    allocationsTable.setColumnSelectionAllowed(false);
    allocationsTable.setCellSelectionEnabled(false);
//    allocationsTable.getActionMap()
    allocationsTable.getActionMap().put(KeyStroke.getKeyStroke("DOWN"), allocationsTable.getActionMap().get("selectNextRow"));
    allocationsTable.getActionMap().put(KeyStroke.getKeyStroke("UP"), allocationsTable.getActionMap().get("selectPreviousRow"));


    allocationsTable.addMouseListener(new MouseListener() {

      public void mouseClicked(MouseEvent e) {
        System.out.println("e = " + e);
      }

      public void mousePressed(MouseEvent e) { }

      public void mouseReleased(MouseEvent e) { }

      public void mouseEntered(MouseEvent e) { }

      public void mouseExited(MouseEvent e) { }
    });

//    transactionAllocationSplit.getLeftComponent()
//        .setMinimumSize(transactionInfoPanel.getLayout().minimumLayoutSize(transactionInfoPanel));
//    allocationsTable.setMinimumSize(new Dimension(200, 200));
    transactionAllocationSplit.getRightComponent().setMinimumSize(new Dimension(200, 200));
//    transactionAllocationSplit.setResizeWeight(0.25);


//    transactionAllocationSplit.getRightComponent().setMinimumSize(allocationsPanel.getLayout().minimumLayoutSize(allocationsPanel));
    transactionAllocationSplit.setContinuousLayout(true);
    transactionAllocationSplit.setOneTouchExpandable(false);

    entity.setFont(entity.getFont().deriveFont(Font.PLAIN));

    ButtonGroup transactionTypeButtonGroup = new ButtonGroup();
    transactionTypeButtonGroup.add(typeExpenseRadio);
    transactionTypeButtonGroup.add(typeIncomeRadio);

    typeExpenseRadio.setFont(typeExpenseRadio.getFont().deriveFont(Font.PLAIN));
    typeIncomeRadio.setFont(typeExpenseRadio.getFont().deriveFont(Font.PLAIN));


    typeExpenseRadio.setAction(new AbstractAction(Strings.get("expense")) {
      public void actionPerformed(ActionEvent e) {
        setViewIsExpense(true);
      }
    });
    typeIncomeRadio.setAction(new AbstractAction(Strings.get("income")) {
      public void actionPerformed(ActionEvent e) {
        setViewIsExpense(false);
      }
    });

    transactionTypeButtonGroup.setSelected(typeExpenseRadio.getModel(), true);
    typeExpenseRadio.doClick();

    transactionDate.setDate(new Date());

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

    Runnable r = new Runnable() {
      public void run() {
        StatusRunnable sr;
        while (true) {
          while (updateQueue.size() > 1) {
            try {
              updateQueue.take(); // throw away stuff
            } catch (InterruptedException ignore) {}
          }
          // take the next
          try {
            sr = updateQueue.take();
            StatusBar.getInstance().addTask(sr.getElement());
            sr.run();
            StatusBar.getInstance().removeTask(sr.getElement());
          } catch (InterruptedException ignore) {}
        }
      }
    };

    new Thread(r, "FormFiller").start();

  }

  public void setViewIsExpense(boolean expense) {
    if (transactionChangeHandler != null) {
      transactionChangeHandler.setExpense(expense);
    }
  }


  public void loadTransactionForId(final int id) {
    if (!MainFrame.getInstance().isTransactionViewShowing()) return;
    if (transactionChangeHandler == null || !transactionChangeHandler.getTransaction().getId().equals(id)) {

      StatusRunnable r = new StatusRunnable(MessageFormat.format(Strings.get("retrieving.transaction"), id)) {
        public void run() {
          try {
            final HibernatePortal hp = new HibernatePortal();

            hp.detachedCriteriaQueryList(
                CriteriaFactory.getInstance().getCategoriesforBudget(State.getInstance().getBudget()),
                true,
                new OutputListener() {
                  public void commandOutputOccurred(OutputEvent event) {
                    if (event.getIndex() == 0) categoriesComboBox.removeAllItems();
                    categoriesComboBox.addItem(event.getPayload());
                  }
                });

            Transaction query = hp.load(Transaction.class, id);
            if (transactionChangeHandler == null)
              transactionChangeHandler = new TransactionChangeHandler(query, TransactionForm.this);
            else
              transactionChangeHandler.importNew(query, TransactionForm.this);
          } catch (AbortException ignore) {
          } catch (InvocationTargetException ignore) {
          } catch (InterruptedException ignore) {
          }
        }
      };
      updateQueue.add(r);
    }
  }

  public synchronized void refreshEntities() throws AbortException {
    entity.removeAllItems();
    for (String e : State.getInstance().entities())
      entity.addItem(e);
  }

  public JPanel getTransactionFormPanel() {
    return transactionFormPanel;
  }

  public void setInboxLabel(String inboxLabel) {
    this.inboxLabel.setText(inboxLabel);
  }

  public void setBalanceLabel(String inboxLabel) {
    this.balanceLabel.setText(inboxLabel);
  }

  public void setOutboxLabel(String inboxLabel) {
    this.outboxLabel.setText(inboxLabel);
  }

  /** Method generated by IntelliJ IDEA GUI Designer >>> IMPORTANT!! <<< DO NOT edit this method OR call it in your code! */
  private void $$$setupUI$$$() {
    createUIComponents();
    transactionFormPanel = new JPanel();
    transactionFormPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    transactionFormPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), null));
    splitpanePanel = new JPanel();
    splitpanePanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), 0, 0));
    transactionFormPanel.add(splitpanePanel,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    splitpanePanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), null));
    transactionAllocationSplit = new JSplitPane();
    splitpanePanel.add(transactionAllocationSplit,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0,
            false));
    transactionInfoPanel = new JPanel();
    transactionInfoPanel.setLayout(new GridLayoutManager(8, 3, new Insets(0, 0, 0, 0), 1, 5));
    transactionAllocationSplit.setLeftComponent(transactionInfoPanel);
    transactionInfoPanel.setBorder(BorderFactory
        .createTitledBorder(ResourceBundle.getBundle("net/lump/envelope/client/ui/defs/Strings").getString("transaction")));
    dateLabel = new JLabel();
    this.$$$loadLabelText$$$(dateLabel, ResourceBundle.getBundle("net/lump/envelope/client/ui/defs/Strings").getString("date"));
    transactionInfoPanel.add(dateLabel,
        new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    entityLabel = new JLabel();
    this.$$$loadLabelText$$$(entityLabel,
        ResourceBundle.getBundle("net/lump/envelope/client/ui/defs/Strings").getString("paid.to"));
    transactionInfoPanel.add(entityLabel,
        new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    entity = new JComboBox();
    entity.setEditable(true);
    transactionInfoPanel.add(entity, new GridConstraints(2, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
        null, 0, false));
    descriptionLabel = new JLabel();
    this.$$$loadLabelText$$$(descriptionLabel,
        ResourceBundle.getBundle("net/lump/envelope/client/ui/defs/Strings").getString("description"));
    transactionInfoPanel.add(descriptionLabel,
        new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    description = new JTextField();
    transactionInfoPanel.add(description,
        new GridConstraints(3, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, 1,
            GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    amountLabel = new JLabel();
    this.$$$loadLabelText$$$(amountLabel, ResourceBundle.getBundle("net/lump/envelope/client/ui/defs/Strings").getString("amount"));
    transactionInfoPanel.add(amountLabel,
        new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    typeExpenseRadio = new JRadioButton();
    this.$$$loadButtonText$$$(typeExpenseRadio,
        ResourceBundle.getBundle("net/lump/envelope/client/ui/defs/Strings").getString("expense"));
    transactionInfoPanel.add(typeExpenseRadio,
        new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
            null, null, 0, false));
    amount = new MoneyTextField();
    amount.setColumns(15);
    transactionInfoPanel.add(amount, new GridConstraints(4, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    transactionInfoPanel.add(transactionDate,
        new GridConstraints(1, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    transactionInfoPanel.add(spacer1,
        new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
            GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    typeIncomeRadio = new JRadioButton();
    this.$$$loadButtonText$$$(typeIncomeRadio,
        ResourceBundle.getBundle("net/lump/envelope/client/ui/defs/Strings").getString("income"));
    transactionInfoPanel.add(typeIncomeRadio,
        new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
            null, null, 0, false));
    typeLabel = new JLabel();
    this.$$$loadLabelText$$$(typeLabel, ResourceBundle.getBundle("net/lump/envelope/client/ui/defs/Strings").getString("type"));
    transactionInfoPanel.add(typeLabel,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    imbalanceMessagePanel = new JPanel();
    imbalanceMessagePanel.setLayout(new GridLayoutManager(1, 1, new Insets(3, 3, 3, 3), -1, -1));
    transactionInfoPanel.add(imbalanceMessagePanel,
        new GridConstraints(7, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    imbalanceMessageLabel = new JLabel();
    imbalanceMessageLabel.setText("");
    imbalanceMessagePanel.add(imbalanceMessageLabel,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    saveStatePanel = new JPanel();
    saveStatePanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    transactionInfoPanel.add(saveStatePanel,
        new GridConstraints(6, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    saveStateLabel = new JLabel();
    saveStateLabel.setText("");
    saveStatePanel.add(saveStateLabel,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    allocationsPanel = new JPanel();
    allocationsPanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), 0, 0));
    transactionAllocationSplit.setRightComponent(allocationsPanel);
    allocationsPanel.setBorder(BorderFactory
        .createTitledBorder(ResourceBundle.getBundle("net/lump/envelope/client/ui/defs/Strings").getString("allocations")));
    allocationsScrollPane = new JScrollPane();
    allocationsPanel.add(allocationsScrollPane,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    allocationsScrollPane.setViewportView(allocationsTable);
    totalsPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    allocationsPanel.add(totalsPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    inboxLabel = new JLabel();
    inboxLabel.setText("");
    totalsPanel.add(inboxLabel,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    balanceLabel = new JLabel();
    balanceLabel.setText("");
    totalsPanel.add(balanceLabel,
        new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    outboxLabel = new JLabel();
    outboxLabel.setText("");
    totalsPanel.add(outboxLabel,
        new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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

  public static class LimitDocument extends PlainDocument {
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

    transactionDate = new JDateChooser(new Date(today), "MM/dd/yyyy", new JTextFieldDateEditor("MM/dd/yyyy", "##/##/####", '_'));
    //transactionDate.setFont(Fonts.fixed.getFont());
    totalsGridLayout = new GridLayout(0, 2);
    totalsPanel = new JPanel(totalsGridLayout);
    totalsPanel.removeAll();
    transactionDate.setPreferredSize(
        new Dimension(transactionDate.getPreferredSize().width + 30, transactionDate.getPreferredSize().height));

    allocationsTable = new JTable() {
      private final KeyStroke tabKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0);

      public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
        AWTEvent currentEvent = EventQueue.getCurrentEvent();
        if (currentEvent instanceof KeyEvent) {
          KeyEvent ke = (KeyEvent)currentEvent;
          if (ke.getSource() != this)
            return;
          // focus change with keyboard
          if (rowIndex == 0 && columnIndex == 0 && KeyStroke.getKeyStrokeForEvent(ke).equals(tabKeyStroke)) {
            tableModel.addEmptyRow();
            rowIndex = getRowCount() - 1;
          }
        }
        super.changeSelection(rowIndex, columnIndex, toggle, extend);
      }
    };
  }

/*
  ItemListener entityItemChangeListener = new ItemListener() {
    public void itemStateChanged(ItemEvent e) {
      if (!originalTransaction.getEntity().equals(e.getItem())) {
        editingTransaction.setEntity((String)entity.getEditor().getItem());
        scheduleDirtyTimer(500);
      }
    }
  };
*/

  public JTable getAllocationsTable() {
    return allocationsTable;
  }

  public JRadioButton getTypeExpenseRadio() {
    return typeExpenseRadio;
  }

  public JRadioButton getTypeIncomeRadio() {
    return typeIncomeRadio;
  }

  public JComboBox<String> getEntity() {
    return entity;
  }

  public JTextField getDescription() {
    return description;
  }

  public JPanel getTransactionInfoPanel() {
    return transactionInfoPanel;
  }

  public JDateChooser getTransactionDate() {
    return transactionDate;
  }

  public MoneyTextField getAmount() {
    return amount;
  }

  public JPanel getTotalsPanel() {
    return totalsPanel;
  }

  public JPanel getImbalanceMessagePanel() {
    return imbalanceMessagePanel;
  }

  public JLabel getImbalanceMessageLabel() {
    return imbalanceMessageLabel;
  }

  public AllocationFormTableModel getTableModel() {
    return tableModel;
  }

  public JSplitPane getTransactionAllocationSplit() {
    return transactionAllocationSplit;
  }

  public JLabel getEntityLabel() {
    return entityLabel;
  }

  public TransactionChangeHandler getTransactionChangeHandler() {
    return transactionChangeHandler;
  }


  public void setSaveStateLabel(String text) {
    saveStateLabel.setText(text);
  }
}
