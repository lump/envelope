package net.lump.envelope.client.ui.components.models;

import net.lump.envelope.client.CriteriaFactory;
import net.lump.envelope.client.portal.HibernatePortal;
import net.lump.envelope.client.portal.TransactionPortal;
import net.lump.envelope.client.thread.StatusElement;
import net.lump.envelope.client.thread.StatusRunnable;
import net.lump.envelope.client.thread.ThreadPool;
import net.lump.envelope.client.ui.components.Hierarchy;
import net.lump.envelope.client.ui.components.StatusBar;
import net.lump.envelope.client.ui.defs.Strings;
import net.lump.envelope.shared.command.OutputEvent;
import net.lump.envelope.shared.command.OutputListener;
import net.lump.envelope.shared.exception.AbortException;
import net.lump.lib.Money;

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
 * @version $Id: TransactionTableModel.java,v 1.42 2009/10/02 22:06:23 troy Exp $
 */
public class TransactionTableModel extends AbstractTableModel {
  private Vector<Object[]> transactions = new Vector<Object[]>();
  private Money beginningBalance = new Money(0);
  private Money beginningReconciledBalance = new Money(0);
  private boolean isTransaction;
  private Date beginDate;
  private Date endDate;
  private Object thing;
  private JTable table;
  private Integer selectionCache = null;
  private Integer rowCount = 0;

  private int filled = -1;
  private long startDate = -1;


  private BlockingQueue<Task> q = new LinkedBlockingQueue<Task>();

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
      selectionCache = (Integer)transactions.get(i)[COLUMN.TransactionID.ordinal()];
  }

  public TransactionTableModel(final Object thing, final Date begin, final Date end, final JTable table) {

    this.table = table;

    final Runnable r = new Runnable() {
      public synchronized void run() {
        Task t = null;

        while (true) {

          try {
            // if there's something else in the queue,
            // abort everything until the last one.
            while (q.size() > 1) {
              List<Task> l = new ArrayList<Task>();
              q.drainTo(l, q.size() - 1);
              for (Task tt : l) tt.finish();
              Thread.sleep(100);
            }
            // take the next
            t = q.take();
            final StatusElement statusElement = t.statusElement;


            int selectedRow = table.getSelectedRow();
            if (selectedRow > -1 && (transactions.size() > selectedRow))
              selectionCache = (Integer)transactions.get(selectedRow)[COLUMN.TransactionID.ordinal()];

            SwingUtilities.invokeAndWait(new Runnable() {
              public void run() {
                table.clearSelection();
              }
            });

            beginDate = t.begin;
            endDate = t.end;
            TransactionTableModel.this.thing = t.thing;
            isTransaction = TransactionTableModel.this.thing instanceof Hierarchy.AccountTotal;

            if (!(TransactionTableModel.this.thing instanceof Hierarchy.AccountTotal
                || TransactionTableModel.this.thing instanceof Hierarchy.CategoryTotal))
              throw new IllegalArgumentException("only AccountTotal or CategoryTotal aceptable as first argument");

            startDate = System.currentTimeMillis();

            CriteriaFactory cf = CriteriaFactory.getInstance();
            HibernatePortal hp = new HibernatePortal();

            hp.detachedCriteriaQueryUnique(
                cf.getBeginningBalance(TransactionTableModel.this.thing, beginDate, null),
                new OutputListener() {
                  public void commandOutputOccurred(OutputEvent event) {
                    beginningBalance = event.getPayload() != null ? (Money)event.getPayload() : new Money(0);
                  }
                });

            hp.detachedCriteriaQueryUnique(
                cf.getBeginningBalance(TransactionTableModel.this.thing, beginDate, Boolean.TRUE),
                new OutputListener() {
                  public void commandOutputOccurred(OutputEvent event) {
                    beginningReconciledBalance = event.getPayload() != null ? (Money)event.getPayload() : new Money(0);
                  }
                });

            final Boolean[] finished = new Boolean[]{Boolean.FALSE};

            List retval = hp.detachedCriteriaQueryList(
                cf.getTransactions(TransactionTableModel.this.thing, beginDate, endDate),
                new OutputListener() {
              public void commandOutputOccurred(OutputEvent event) {
                synchronized (finished) {
                  StatusBar sb = StatusBar.getInstance();

                  if (sb.getProgress().isVisible()) {
                    if (sb.getProgress().getMinimum() != 0) sb.getProgress().setMinimum(0);
                    if (sb.getProgress().getMaximum() != event.getTotal().intValue())
                      sb.getProgress().setMaximum(event.getTotal().intValue());
                    sb.getProgress().setValue(event.getIndex().intValue());
                  }
                  else if (startDate < (System.currentTimeMillis() - 150)) sb.getProgress().setVisible(true);

                  statusElement.setValue(
                      Strings.get("reading") + " " + TransactionTableModel.this.thing + " row " + (event.getIndex() + 1L));
                  StatusBar.getInstance().updateLabel();

                  updateTableFor(event.getIndex().intValue(), (Object[])event.getPayload());
                  rowCount = event.getIndex().intValue() + 1;

                  if (event.getIndex().equals(event.getTotal() - 1)) {
                    finished[0] = Boolean.TRUE;
                    if (sb.getProgress().isVisible()) sb.getProgress().setVisible(false);
                    finished.notifyAll();
                  }
                }
              }
            });

            if (retval.size() == 0) rowCount = 0;

            long startTime = System.currentTimeMillis();
            // wait until filled (so other things in the queue can't start and conflict)
            try {
              synchronized (finished) {
                while ((!finished[0] && filled != rowCount) && (System.currentTimeMillis() - 20000) > startTime)
                  finished.wait(1000);
              }
            } catch (InterruptedException e) {
              // KTHXBYE
            }

            t.finish();
            t = null;

            // nuke any rows not needed in the table
            if (rowCount < transactions.size()) {
              int oldSize = transactions.size();
              transactions.setSize(rowCount);
              fireTableRowsDeleted(rowCount, oldSize - 1);
            }

          } catch (InterruptedException ignore) {
            break;
          } catch (AbortException ignore) {} catch (Exception e) {
            e.printStackTrace();
          } finally {
            if (t != null) t.finish();
          }
        }
      }
    };

    new Thread(r, "Transaction table filler queue").start();
    queue(thing, begin, end);
  }

  private synchronized void updateTableFor(final int rowNumber, Object[] row) {

    if (rowNumber > filled + 1) System.err.println("NON-SEQUENTIAL " + rowNumber + " for " + filled);
    filled = rowNumber;

    if (rowNumber != 0 && ((rowNumber - 1) > (transactions.size() - 1))) System.err
        .println("transactions doesn't have row " + (rowNumber - 1) + " as transactions is size: " + (transactions.size() - 1));

    // refresh our amnesia on the balances
    Money reconciled = rowNumber == 0 ? beginningBalance : (Money)transactions.get(rowNumber - 1)[COLUMN.Reconciled.ordinal()];
    Money balance = rowNumber == 0 ? beginningReconciledBalance : (Money)transactions.get(rowNumber - 1)[COLUMN.Balance.ordinal()];

    // calculate balance column
    balance = new Money(balance.add((Money)row[COLUMN.Amount.ordinal()]));

    // only add reconciled balance if it tx is reconciled
    if ((Boolean)row[COLUMN.C.ordinal()]) reconciled = new Money(reconciled.add((Money)row[COLUMN.Amount.ordinal()]));

    // create our new row
    final Object[] newRow = new Object[]{row[0], row[1], row[2], new Money(balance), new Money(reconciled), row[3], row[4], row[5]};

    if (rowNumber < transactions.size()) transactions.set(rowNumber, newRow);
    else transactions.add(rowNumber, newRow);

    final boolean reselect = (selectionCache != null && newRow[COLUMN.TransactionID.ordinal()].equals(selectionCache));
    // make sure table updates happen on swing thread to avoid deadlocks

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        fireTableRowsInserted(rowNumber, rowNumber);
        if (reselect) table.changeSelection(rowNumber, 0, false, false);
        if (table.getSelectedRow() == -1) table.scrollRectToVisible(table.getCellRect(table.getRowCount(), 0, true));
      }
    });

    // let other threads go.
    Thread.yield();
  }


  public void queue(Object thing, Date begin, Date end) {
    try {
      q.put(new Task(thing, begin, end));
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

  public void setValueAt(final Object value, final int row, final int col) {
    if (col == COLUMN.C.ordinal()) {
      // update the column
      transactions.get(row)[col] = value;
      fireTableCellUpdated(row, col);

// establish the beginning reconciled balance
      Money reconciled = row == 0 ? beginningReconciledBalance : (Money)transactions.get(row - 1)[COLUMN.Reconciled.ordinal()];

// step through each row beginning with the row we're on
// and re-total the reconciled column
      for (int x = row; x < transactions.size(); x++) {
        if ((Boolean)transactions.get(x)[COLUMN.C.ordinal()])
          reconciled = new Money(reconciled.add((Money)transactions.get(x)[COLUMN.Amount.ordinal()]));
        transactions.get(x)[COLUMN.Reconciled.ordinal()] = new Money(reconciled);
        fireTableCellUpdated(x, COLUMN.Reconciled.ordinal());
      }

      // update the Transaction
      ThreadPool.getInstance().execute(new StatusRunnable(
          MessageFormat.format("{0} {1} {2}", ((Boolean)value) ? Strings.get("reconciling") : Strings.get("unreconciling"),
              Strings.get("transaction").toLowerCase(), transactions.get(row)[COLUMN.TransactionID.ordinal()])) {

        public void run() {
          try {
            new TransactionPortal()
                .updateReconciled((Integer)transactions.get(row)[COLUMN.TransactionID.ordinal()], (Boolean)value);
          } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), Strings.get("error"), JOptionPane.ERROR_MESSAGE);
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

  public Integer getTransactionId(Integer row) {
    return (Integer)transactions.get(row)[COLUMN.TransactionID.ordinal()];
  }

  class Task {
    StatusElement statusElement;
    Object thing;
    Date begin;
    Date end;

    Task(Object thing, Date begin, Date end) {
      this.thing = thing;
      this.begin = begin;
      this.end = end;
      final String type =
          thing instanceof Hierarchy.AccountTotal ? Strings.get("account").toLowerCase() : Strings.get("category").toLowerCase();
      statusElement =
          StatusBar.getInstance().addTask(MessageFormat.format("{0} {1} {2}", Strings.get("retrieving"), thing.toString(), type));
    }

    public void finish() {
      StatusBar.getInstance().removeTask(statusElement);
    }

  }
}
