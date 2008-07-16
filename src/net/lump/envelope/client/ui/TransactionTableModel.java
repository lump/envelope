package us.lump.envelope.client.ui;

import us.lump.envelope.entity.Account;
import us.lump.envelope.entity.Identifiable;
import us.lump.envelope.entity.Category;
import us.lump.envelope.entity.Transaction;
import us.lump.envelope.client.CriteriaFactory;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.client.portal.HibernatePortal;
import us.lump.envelope.client.portal.TransactionPortal;
import us.lump.envelope.exception.EnvelopeException;
import us.lump.lib.Money;

import javax.swing.table.DefaultTableModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.event.TableModelListener;
import javax.swing.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.Vector;

/**
 * A table model which lists transactions.
 * @author Troy Bowman
 * @version $Id: TransactionTableModel.java,v 1.6 2008/07/16 05:40:00 troy Exp $
 */
public class TransactionTableModel extends AbstractTableModel {
//  private List<Transaction> transactions;
  private Vector<Object[]> transactions;
  private Money beginningBalance;
  private Money beginningReconciledBalance;
  private boolean isTransaction;

  public static final int ID = 7;
  public static final int RECONCILED_FLAG = 0;
  public static final int AMOUNT = 2;
  public static final int RECONCILED = 4;

  String[] columnNames = new String[]{"C","Date","Amount","Balance","Reconciled","Entity","Description"};

  TransactionTableModel(Identifiable categoryOrAccount, Date beginDate, Date endDate) {
    if (!(categoryOrAccount instanceof Account || categoryOrAccount instanceof Category))
      throw new IllegalArgumentException("only Account or Budget aceptable as first argument");
    isTransaction = categoryOrAccount instanceof Account ? true : false;

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
      balance = new Money(balance.add((Money)row[AMOUNT]));
      if ((Boolean)row[RECONCILED_FLAG]) reconciled = new Money(reconciled.add((Money)row[AMOUNT]));
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
                         : (Money)transactions.get(row - 1)[RECONCILED];
      // step through each row beginning with the row we're on
      // and re-total the reconciled column
      for (int x = row; x < transactions.size(); x++) {
        if ((Boolean)transactions.get(x)[RECONCILED_FLAG])
          reconciled
              = new Money(reconciled.add((Money)transactions.get(x)[AMOUNT]));
        transactions.get(x)[RECONCILED] = new Money(reconciled);
        fireTableCellUpdated(x,RECONCILED);
      }

      // update the Transaction
//      SwingUtilities.invokeLater(new Runnable() {
//        public void run() {
          try {
//            Thread.sleep(5);
            new TransactionPortal().updateReconciled(
                (Integer)transactions.get(row)[ID], (Boolean)aValue);
          } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), Strings.get("error"), JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
          }
//        }
//      });
    }
  }

//  public void addTableModelListener(TableModelListener l) {
//  }

//  public void removeTableModelListener(TableModelListener l) {
//  }

  public String getColumnName(int columnIndex) {
    return columnNames[columnIndex];
  }

  public Class<?> getColumnClass(int columnIndex) {
    return transactions.get(0)[columnIndex].getClass();
  }

  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return columnIndex == 0 && isTransaction;
  }

}
