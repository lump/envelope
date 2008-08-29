package us.lump.envelope.client.ui.components.models;

import us.lump.envelope.client.CriteriaFactory;
import us.lump.envelope.client.portal.TransactionPortal;
import us.lump.envelope.client.thread.EnvelopeRunnable;
import us.lump.envelope.client.thread.StatusElement;
import us.lump.envelope.client.thread.ThreadPool;
import us.lump.envelope.client.ui.components.StatusBar;
import us.lump.envelope.client.ui.components.forms.TableQueryBar;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.entity.Account;
import us.lump.envelope.entity.Category;
import us.lump.envelope.entity.Identifiable;
import us.lump.lib.Money;
import us.lump.lib.util.BackgroundList;
import us.lump.lib.util.BackgroundListEvent;
import us.lump.lib.util.BackgroundListListener;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
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
  private JTable table;
  private Integer selectionCache = null;

  private int filled = -1;
  private long startDate = -1;


  private BlockingQueue<Task> q =
      new LinkedBlockingQueue<Task>();

  public static enum COLUMN {
    C,
    Date,
    Amount,
    Balance,
    Reconciled,
    Entity,
    Description,
    TransactionID,
    AllocationID
  }

  public void setSelectionCache(Integer i) {
    if (i != null && i > -1 && i < transactions.size())
      selectionCache =
          (Integer)transactions.get(i)[COLUMN.TransactionID.ordinal()];
  }

  private Money getBeginningBalance(Boolean reconciled) {
    CriteriaFactory cf = CriteriaFactory.getInstance();
    return cf.getBeginningBalance(identifiable, beginDate, reconciled);
  }

  public TransactionTableModel(final Identifiable categoryOrAccount,
                               final Date begin,
                               final Date end,
                               final JTable table) {

    this.table = table;

    new Thread(new Runnable() {
      public synchronized void run() {
        try {
          while (true) {

            // if there's something else in the queue,
            // abort everything until the last one.
            while (q.size() > 1) {
              List<Task> l = new ArrayList<Task>();
              q.drainTo(l, q.size() - 1);
              for (Task t : l) t.finish();
              Thread.sleep(100);
            }
            // take the next
            Task t = q.take();

            int selectedRow = table.getSelectedRow();
            if (selectedRow > -1)
              selectionCache =
                  (Integer)transactions.get(selectedRow)[COLUMN.TransactionID
                      .ordinal()];


            beginDate = t.begin;
            endDate = t.end;
            identifiable = t.categoryOrAccount;
            isTransaction = identifiable instanceof Account;

            if (!(identifiable instanceof Account
                  || identifiable instanceof Category))
              throw new IllegalArgumentException(
                  "only Account or Budget aceptable as first argument");

            startDate = System.currentTimeMillis();

            CriteriaFactory cf = CriteriaFactory.getInstance();
            final BackgroundList<Object[]> incoming = (BackgroundList<Object[]>)
                cf.getTransactions(identifiable, beginDate, endDate);

            // boostrap statusbar
            StatusBar.getInstance().getProgress().setMinimum(0);
            StatusBar.getInstance().getProgress().setMaximum(incoming.size());

            // nuke any rows not needed in the table
            if (incoming.size() < transactions.size()) {
              int oldSize = transactions.size();
              transactions.setSize(incoming.size());
              fireTableRowsDeleted(incoming.size(), oldSize - 1);
            }

            filled = 0;
            updateTableForRow(incoming.filledSize(), incoming);

            incoming.addBackgroundListListener(new BackgroundListListener() {
              public void backgroundListEventOccurred(BackgroundListEvent event) {
                synchronized (incoming) {
                  if (event.getType() == BackgroundListEvent.Type.filled) {
//                    try {
//                      Thread.sleep(50);
//                    } catch (InterruptedException e) {
////                       blah
//                    }
//                    updateTableToFrom(incoming.size(), incoming);
                    StatusBar.getInstance().getProgress().setVisible(false);
                  }
                  if (event.getType() == BackgroundListEvent.Type.added) {
                    updateTableForRow(event.getRow(), incoming);

                    if (startDate < (System.currentTimeMillis() - 100)) {
                      StatusBar.getInstance().getProgress().setVisible(true);
                    }
                    if (StatusBar.getInstance().getProgress().isVisible()) {
                      StatusBar.getInstance().getProgress().setValue(filled);
                    }
                  }
                }
                Thread.yield();
              }

//                if (q.size() > 0
//                    && event.getType() == BackgroundListEvent.Type.aborted) {
//                  incoming.fireAbort();
//                }

            });

            Thread.yield();
//            if (incoming.filled()) incoming.fireAllFilled();

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

  private synchronized void updateTableForRow(int x,
                                              BackgroundList<Object[]> incoming) {
    if (x < 0) return;
    if (x > filled + 1) {
      for (int row = filled; row <= x; row++) updateTableFor(row, incoming);
    } else updateTableFor(x, incoming);
  }

  private synchronized void updateTableFor(int x,
                                           BackgroundList<Object[]> incoming) {
    if (x > filled + 1) {
      System.err.println("gah!");
    }
    filled = x;
    Object[] row = incoming.get(x);

    if (x != 0 && (x - 1) > (transactions.size() - 1)) {
      System.err.println("blabla");
    }

    // refresh our amnesia on the balances
    Money reconciled =
        x == 0 ? getBeginningBalance(Boolean.TRUE)
        : (Money)transactions.get(x - 1)[COLUMN.Reconciled.ordinal()];
    Money balance =
        x == 0 ? getBeginningBalance(Boolean.FALSE)
        : (Money)transactions.get(x - 1)[COLUMN.Balance.ordinal()];

    // calculate balance column
    balance = new Money(balance.add(
        (Money)row[COLUMN.Amount.ordinal()]));

    // only add reconciled balance if it tx is reconciled
    if ((Boolean)row[COLUMN.C.ordinal()]) reconciled =
        new Money(reconciled.add(
            (Money)row[COLUMN.Amount.ordinal()]));

    // create our new row
    Object[] newRow = new Object[]{
        row[0], row[1], row[2], new Money(balance),
        new Money(reconciled), row[3], row[4], row[5]};

    if (x < transactions.size()) {
      transactions.set(x, newRow);
      fireTableRowsUpdated(x, x);
    } else {
      transactions.add(x, newRow);
      fireTableRowsInserted(x, x);
    }

    if (selectionCache != null
        && newRow[COLUMN.TransactionID.ordinal()]
        .equals(selectionCache)) {
      table.getSelectionModel().clearSelection();
      table.changeSelection(x, 0, false, false);
    }

    TableQueryBar.getInstance().getTxCount()
        .setText(String.valueOf(filled + 1));
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
    if (rowIndex >= transactions.size()) {
      fireTableRowsDeleted(rowIndex, rowIndex);
      return null;
    }
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
                               transactions.get(row)[COLUMN.TransactionID
                                   .ordinal()])) {

        public void run() {
          try {
            new TransactionPortal().updateReconciled(
                (Integer)transactions.get(row)[COLUMN.TransactionID.ordinal()],
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
}
