package us.lump.envelope.client.ui.components.models;

import us.lump.envelope.client.CriteriaFactory;
import us.lump.envelope.client.portal.TransactionPortal;
import us.lump.envelope.client.thread.EnvelopeRunnable;
import us.lump.envelope.client.thread.ThreadPool;
import us.lump.envelope.client.thread.StatusElement;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.client.ui.components.StatusBar;
import us.lump.envelope.entity.Account;
import us.lump.envelope.entity.Category;
import us.lump.envelope.entity.Identifiable;
import us.lump.lib.Money;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.text.MessageFormat;
import java.util.Date;
import java.util.Vector;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A table model which lists transactions.
 *
 * @author Troy Bowman
 * @version $Id: TransactionTableModel.java,v 1.6 2008/07/16 05:40:00 troy Exp
 *          $
 */
public class TransactionTableModel extends AbstractTableModel {
  private Vector<Object[]> transactions = new Vector<Object[]>();
  private Money beginningBalance = new Money(0);
  private Money beginningReconciledBalance = new Money(0);
  private boolean isTransaction;
  private Date beginDate;
  private Date endDate;
  private Identifiable identifiable;

  private BlockingQueue<Task> q =
      new LinkedBlockingQueue<Task>();

  public static enum COLUMN {
    C, Date, Amount, Balance, Reconciled, Entity, Description, ID
  }

  public TransactionTableModel(final Identifiable categoryOrAccount,
                               final Date begin,
                               final Date end) {

    new Thread(new Runnable() {
      public synchronized void run() {
        try {
          while (true) {

            // if there's something else in the queue, abort it.
            while (q.size() > 1) {
              List<Task> l = new ArrayList<Task>();
              q.drainTo(l, q.size()-1);
              for (Task t : l) t.finish();
              Thread.sleep(100);
            }
            // take the last one.
            Task t = q.take();

            beginDate = t.begin;
            endDate = t.end;
            identifiable = t.categoryOrAccount;
            isTransaction = identifiable instanceof Account;

            if (!(identifiable instanceof Account
                  || identifiable instanceof Category))
              throw new IllegalArgumentException(
                  "only Account or Budget aceptable as first argument");


            CriteriaFactory cf = CriteriaFactory.getInstance();
            beginningBalance
                = cf.getBeginningBalance(identifiable, beginDate, null);
            Money balance = beginningBalance;
            beginningReconciledBalance
                = cf.getBeginningBalance(identifiable, beginDate, true);
            Money reconciled = beginningReconciledBalance;

            int oldSize = transactions.size();
            transactions = new Vector<Object[]>();

            Vector<Object[]> incoming =
                cf.getTransactions(identifiable, beginDate, endDate);
            for (int x = 0; x < incoming.size(); x++) {
              Object[] row = incoming.get(x);
              balance =
                  new Money(balance.add((Money)row[COLUMN.Amount.ordinal()]));
              if ((Boolean)row[COLUMN.C.ordinal()])
                reconciled = new Money(reconciled.add((Money)row[COLUMN.Amount
                    .ordinal()]));
              transactions.add(
                  new Object[]{row[0], row[1], row[2], new Money(balance),
                               new Money(reconciled), row[3], row[4], row[5]});
              fireTableRowsInserted(x, x);
              if (x <= oldSize) fireTableRowsUpdated(x, x);
              else fireTableRowsInserted(x, x);
            }

            if (oldSize > incoming.size())
              fireTableRowsDeleted(incoming.size(), oldSize + 1);

            t.finish();
          }
        }

        catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }, "Transaction Table Filler").start();

    queue(categoryOrAccount, begin, end);
  }

  class Task {
    Task(Identifiable categoryOrAccount, Date begin, Date end) {
      this.categoryOrAccount = categoryOrAccount;
      this.begin = begin;
      this.end = end;
      final String type = categoryOrAccount instanceof Account
                          ? Strings.get("account").toLowerCase()
                          : Strings.get("category").toLowerCase();
      e =
          StatusBar.getInstance().addTask(MessageFormat.format(
              "{0} {1} {2}",
              Strings.get("retrieving"),
              categoryOrAccount.toString(),
              type));
    }

    public void finish() {
      StatusBar.getInstance().removeTask(e);
    }

    StatusElement e;
    Identifiable categoryOrAccount;
    Date begin;
    Date end;
  }

  public void queue(Identifiable categoryOrAccount, Date begin, Date end) {
    try {
      q.put(new Task(categoryOrAccount, begin, end));
    } catch (InterruptedException e) {
      e.printStackTrace();
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
    if (transactions.size() == 0) return null;
    return transactions.get(rowIndex)[columnIndex];
  }

  public void setValueAt(final Object aValue, final int row, final int col) {
    if (col == COLUMN.C.ordinal()) {
      // update the column
      transactions.get(row)[col] = aValue;
      fireTableCellUpdated(row, col);

// establish the beginning reconciled balance
      Money reconciled = row == 0
                         ? beginningReconciledBalance
                         : (Money)transactions.get(row - 1)[COLUMN.Reconciled
                             .ordinal()];

// step through each row beginning with the row we're on
// and re-total the reconciled column
      for (int x = row; x < transactions.size(); x++) {
        if ((Boolean)transactions.get(x)[COLUMN.C.ordinal()])
          reconciled = new Money(reconciled.add(
              (Money)transactions.get(x)[COLUMN.Amount.ordinal()]));
        transactions.get(x)[COLUMN.Reconciled.ordinal()] =
            new Money(reconciled);
        fireTableCellUpdated(x, COLUMN.Reconciled.ordinal());
      }

      // update the Transaction
      ThreadPool.getInstance().execute(new EnvelopeRunnable(
          MessageFormat.format("{0} {1} {2}",
                               ((Boolean)aValue)
                               ? Strings.get("reconciling")
                               : Strings.get("unreconciling"),
                               Strings.get("transaction").toLowerCase(),
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
    if (transactions.size() == 0) return null;
    return transactions.get(0)[columnIndex].getClass();
  }

  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return columnIndex == 0 && isTransaction;
  }

}
