package us.lump.envelope.client.ui;

import us.lump.envelope.client.CriteriaFactory;
import us.lump.envelope.client.thread.ThreadPool;
import us.lump.envelope.client.thread.EnvelopeRunnable;
import us.lump.envelope.client.portal.TransactionPortal;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.entity.Account;
import us.lump.envelope.entity.Category;
import us.lump.envelope.entity.Identifiable;
import us.lump.lib.Money;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.Date;
import java.util.Vector;
import java.text.MessageFormat;

/**
 * A table model which lists transactions.
 *
 * @author Troy Bowman
 * @version $Id: TransactionTableModel.java,v 1.6 2008/07/16 05:40:00 troy Exp
 *          $
 */
public class TransactionTableModel extends AbstractTableModel {
  //  private List<Transaction> transactions;
  private Vector<Object[]> transactions;
  private Money beginningBalance;
  private Money beginningReconciledBalance;
  private boolean isTransaction;

  public static enum COLUMN {
    C, Date, Amount, Balance, Reconciled, Entity, Description, ID
  }

  TransactionTableModel(Identifiable categoryOrAccount,
                        Date beginDate,
                        Date endDate) {
    if (!(categoryOrAccount instanceof Account
          || categoryOrAccount instanceof Category))
      throw new IllegalArgumentException(
          "only Account or Budget aceptable as first argument");
    isTransaction = categoryOrAccount instanceof Account;

    CriteriaFactory cf = CriteriaFactory.getInstance();
    transactions = new Vector<Object[]>();
    beginningBalance
        = cf.getBeginningBalance(categoryOrAccount, beginDate, null);
    Money balance = beginningBalance;
    beginningReconciledBalance
        = cf.getBeginningBalance(categoryOrAccount, beginDate, true);
    Money reconciled = beginningReconciledBalance;
    Vector<Object[]> incoming =
        cf.getTransactions(categoryOrAccount, beginDate, endDate);
    for (Object[] row : incoming) {
      balance = new Money(balance.add((Money)row[COLUMN.Amount.ordinal()]));
      if ((Boolean)row[COLUMN.C.ordinal()])
        reconciled =
            new Money(reconciled.add((Money)row[COLUMN.Amount.ordinal()]));
      transactions.add(
          new Object[]{row[0], row[1], row[2], new Money(balance),
                       new Money(reconciled), row[3], row[4], row[5]});
    }
  }

  public Vector<Object[]> getTransactions() {
    return transactions;
  }

  public int getRowCount() {
    return transactions.size();
  }

  public int getColumnCount() {
    return 7;
  }

  public Object getValueAt(int rowIndex, int columnIndex) {
    return transactions.get(rowIndex)[columnIndex];
  }

  public void setValueAt(final Object aValue, final int row, final int col) {
    if (col == 0) {
//      System.out.println("update transaction " + transactions.get(row)[ID]+ " set reconcied = " + aValue);
      // update the column
      transactions.get(row)[col] = aValue;
      fireTableCellUpdated(row, col);

      // establish the beginning reconciled balance
      Money reconciled = row == 0
                         ? beginningReconciledBalance
                         : (Money)transactions.get(row - 1)[COLUMN.Amount.ordinal()];

      // step through each row beginning with the row we're on
      // and re-total the reconciled column
      for (int x = row; x < transactions.size(); x++) {
        if ((Boolean)transactions.get(x)[COLUMN.C.ordinal()])
          reconciled = new Money(reconciled.add(
              (Money)transactions.get(x)[COLUMN.Amount.ordinal()]));
        transactions.get(x)[COLUMN.Reconciled.ordinal()] = new Money(reconciled);
        fireTableCellUpdated(x, COLUMN.Reconciled.ordinal());
      }

      // update the Transaction
      ThreadPool.getInstance().execute(new EnvelopeRunnable(
          MessageFormat.format("{0} {1} {2}",
                               ((Boolean)aValue)
                               ? Strings.get("reconciling")
                               : Strings.get("unreconciling"),
                               Strings.get("transaction"),
                               transactions.get(row)[COLUMN.ID.ordinal()])) {
              
        public void run() {
          try {
            new TransactionPortal().updateReconciled(
                (Integer)transactions.get(row)[COLUMN.ID.ordinal()],
                (Boolean)aValue);
          } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                                          e.getMessage(),
                                          Strings.get("error"),
                                          JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
          }
        }
      });
    }
  }

//  public void addTableModelListener(TableModelListener l) {
//  }

//  public void removeTableModelListener(TableModelListener l) {
//  }

  public String getColumnName(int columnIndex) {
    return COLUMN.values()[columnIndex].toString();
  }

  public Class<?> getColumnClass(int columnIndex) {
    return transactions.get(0)[columnIndex].getClass();
  }

  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return columnIndex == 0 && isTransaction;
  }

}
