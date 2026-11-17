package net.lump.envelope.client.ui.components;

import net.lump.envelope.client.portal.QueryPortal;
import net.lump.envelope.client.portal.TransactionPortal;
import net.lump.envelope.client.thread.StatusElement;
import net.lump.envelope.client.thread.StatusRunnable;
import net.lump.envelope.client.thread.ThreadPool;
import net.lump.envelope.client.ui.components.forms.table_query_bar.TableQueryBar;
import net.lump.envelope.client.ui.defs.Strings;
import net.lump.envelope.shared.command.OutputEvent;
import net.lump.envelope.shared.command.OutputListener;
import net.lump.envelope.shared.entity.Allocation;
import net.lump.envelope.shared.entity.Transaction;
import net.lump.envelope.shared.exception.AbortException;
import net.lump.lib.Money;
import net.lump.lib.util.ByteFormat;
import net.lump.lib.util.Day;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A table model which lists transactions.
 *
 * @author Troy Bowman
 */
public class TransactionTableModel extends AbstractTableModel {
  private Vector<Object[]> transactions = new Vector<Object[]>();
  private Money beginningBalance = new Money(0);
  private Money beginningReconciledBalance = new Money(0);
  private Money incomeBalance = new Money(0);
  private Money outgoingBalance = new Money(0);
  private boolean isTransaction;
  private Date beginDate;
  private Date endDate;
  private Object thing;
  private JTable table;
  private Integer selectionCache = null;
  private Integer rowCount = 0;

  private int filled = -1;
  private long startDate = -1;

  /** true while the filler thread is loading a query, when reflect() stands aside */
  private volatile boolean filling = false;

  /**
   * true while reflect() is swapping rows.  The table's selection listener loads
   * whatever row is selected into the transaction form, and a row swap passes the
   * selection through other rows on its way back -- each of which would queue a
   * load and switch the form away from the transaction being edited.
   */
  private boolean quiet = false;


  private BlockingQueue<Task> q = new LinkedBlockingQueue<Task>();

  public enum COLUMN {
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

  public boolean isTransaction() {
    return isTransaction;
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
            filling = true;
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

            // Empty the table before the first row of the new query can land.
            // Rows were overwritten in place and the surplus trimmed only after a
            // completed fill -- and the AbortException catch below skips that trim
            // entirely -- so a query that failed or was abandoned left the previous
            // selection's rows on screen, or a torn mix of two selections, under a
            // title the tree's selection listener had already changed on the EDT.
            SwingUtilities.invokeAndWait(new Runnable() {
              public void run() {
                int had = transactions.size();
                if (had > 0) {
                  transactions.clear();
                  fireTableRowsDeleted(0, had - 1);
                }
              }
            });

            // An index into the fill that is about to start.  It was never reset,
            // so it carried the previous task's last row number into the wait
            // below.
            filled = -1;

            incomeBalance = new Money(0);
            TableQueryBar.getInstance().setInboxLabel(incomeBalance.toString());
            outgoingBalance = new Money(0);
            TableQueryBar.getInstance().setOutboxLabel(outgoingBalance.toString());
            startDate = System.currentTimeMillis();

            QueryPortal qp = new QueryPortal();

            beginningBalance = qp.beginningBalance(TransactionTableModel.this.thing, beginDate, null);
            beginningReconciledBalance =
                qp.beginningBalance(TransactionTableModel.this.thing, beginDate, Boolean.TRUE);

            final Boolean[] finished = new Boolean[]{Boolean.FALSE};

            List retval = qp.transactions(TransactionTableModel.this.thing, beginDate, endDate,
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
                          Strings.get("reading") + " " + TransactionTableModel.this.thing + " " + (event.getIndex() + 1L)
                          + " rows, " + ByteFormat.getInstance().formatBytes(event.getBytesRead(), true) + ", "
                          + ByteFormat.getInstance().formatBytes(event.getBytesPerSecond(), true) + "/s");
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

            if (retval.size() == 0) {
              rowCount = 0;
              // Nothing will arrive, so the listener below never runs and nothing
              // ever calls notifyAll.  Say it here, or the wait sits out its whole
              // timeout on every empty category and every date range with no rows.
              synchronized (finished) { finished[0] = Boolean.TRUE; }
            }

            long startTime = System.currentTimeMillis();
            // wait until filled (so other things in the queue can't start and conflict)
            try {
              synchronized (finished) {
                // This read (now - 20000) > startTime, which demands that 20
                // seconds have ALREADY passed, so the body never ran: the fill was
                // never actually waited for.  It only went unnoticed because
                // HttpClient delivers its output events inline, so every row has
                // landed by the time we get here.  The "filled != rowCount" half
                // is gone with it -- filled is the last row INDEX and rowCount a
                // count, so it could not have become equal either.  finished[0] is
                // set by the listener on the last row, and above for an empty
                // result.
                while (!finished[0] && (System.currentTimeMillis() - startTime) < 20000)
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
            filling = false;
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

    // refresh our amnesia on the balances.  These two seeds were crossed: Balance
    // started from the reconciled-only sum and Reconciled from the unfiltered one,
    // so with any unreconciled transaction before the begin date every Balance in
    // the list was off by the difference between them, and setValueAt -- which
    // seeds the right way round -- made the first row jump when its checkbox was
    // toggled.  Balance carries every transaction; Reconciled carries only the
    // reconciled ones.
    Money reconciled = rowNumber == 0 ? beginningReconciledBalance : (Money)transactions.get(rowNumber - 1)[COLUMN.Reconciled.ordinal()];
    Money balance = rowNumber == 0 ? beginningBalance : (Money)transactions.get(rowNumber - 1)[COLUMN.Balance.ordinal()];

    Money amount = (Money)row[COLUMN.Amount.ordinal()];
    // calculate balance column

    balance = balance.add(amount);

    if (amount.compareTo(Money.ZERO) > 0) {
      incomeBalance = incomeBalance.add(amount);
      TableQueryBar.getInstance().setInboxLabel(incomeBalance.toString());
    }
    else if (amount.compareTo(Money.ZERO) < 0) {
      outgoingBalance = outgoingBalance.add(amount.abs());
      TableQueryBar.getInstance().setOutboxLabel(outgoingBalance.toString());
    }

    // only add reconciled balance if it tx is reconciled
    if ((Boolean)row[COLUMN.C.ordinal()]) reconciled = reconciled.add((Money)row[COLUMN.Amount.ordinal()]);

    // create our new row
    // The allocation id (a category's rows only) is kept, out of sight past the
    // visible columns: a transaction can hold several rows in a category -- an
    // auto-deduct pair -- and reflect() has to know which row is which.  It used
    // to be dropped here.
    final Object[] newRow = new Object[]{row[0], row[1], row[2], new Money(balance), new Money(reconciled), row[3], row[4], row[5],
        row.length > 6 ? row[6] : null};

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


  /** Whether reflect() is swapping rows; the form's selection listener ignores the table meanwhile. */
  public boolean isQuiet() {
    return quiet;
  }

  /**
   * Bring one transaction's rows in line with the transaction form's copy of it,
   * as the refresh button would show them, without re-reading the table.  The
   * form saves as it is edited, and calls this after each save, so the row above
   * it moves as the user types; the refresh button still does a full re-read.
   * Event thread only.
   *
   * <p>The rows are rebuilt from the form's transaction, counting only saved
   * allocations (what the server would sum).  An account's view gets one row --
   * the transaction's net on that account -- if any allocation is filed under it;
   * a category's view gets one row per allocation in that category, in allocation
   * stamp order, which is how the query orders them within a transaction.  Where
   * it goes: editing an allocation leaves the transaction where it is; saving the
   * transaction's own fields moves its stamp, and the query orders a day's rows by
   * stamp, so it goes last within its day, as a refresh would put it.  Dated past
   * the end of the range, or filed out of the viewed account or category, it
   * leaves the table.  Dated before the range, or joining a table it was not in,
   * it may be moving money into or out of the beginning balance -- a sum the
   * server took, over rows this table never held -- so those fall back to a full
   * re-read rather than guess.  Running balances and the in/out totals are
   * recomputed from the beginning balances.  Nothing happens while a re-read is
   * filling the table -- it is reading the saved state anyway.
   *
   * @param t               the form's transaction
   * @param ownColumnsSaved true when the transaction's own fields were just saved,
   *                        which moves its stamp and so its place within its day
   */
  public void reflect(Transaction t, boolean ownColumnsSaved) {
    if (t == null || t.getId() == null || t.getDate() == null || thing == null || beginDate == null
        || endDate == null || filling || !q.isEmpty()) return;

    final int tid = COLUMN.TransactionID.ordinal();
    Integer id = t.getId();
    String day = Day.toIso(t.getDate());

    List<Object[]> mine = rowsFor(t);
    List<Object[]> others = new ArrayList<Object[]>();
    int first = -1;
    for (Object[] row : transactions) {
      if (id.equals(row[tid])) { if (first < 0) first = others.size(); }
      else others.add(row.clone());
    }
    boolean present = first >= 0;
    if (!present && mine.isEmpty()) return;   // not in this view, before or after

    // Two moves touch money this table does not hold, the beginning balance --
    // the server's sum of everything in the view dated before the range.  One
    // dated before the range now has left the table for it; one not in the table
    // and joining it may be arriving from it (dated before the range until now)
    // or from nowhere (dated past the end, filed elsewhere), and nothing here says
    // which.  Both re-read, which is always right; only rows already in the table,
    // staying in it or leaving past the end, are worked out here.  Added after the
    // first cut double-counted a transaction coming back into the range.
    // The day comparison is the server's: the UTC day of each bound.
    if (day.compareTo(Day.toIso(beginDate)) < 0 || !present) {
      queue(thing, beginDate, endDate);
      return;
    }
    if (day.compareTo(Day.toIso(endDate)) > 0) mine.clear();

    int at;
    if (!ownColumnsSaved) at = first;
    else {
      at = others.size();
      for (int i = 0; i < others.size(); i++)
        if (Day.toIso((Date)others.get(i)[COLUMN.Date.ordinal()]).compareTo(day) > 0) { at = i; break; }
    }

    List<Object[]> rebuilt = new ArrayList<Object[]>(others);
    rebuilt.addAll(at, mine);
    Money[] inOut = rebalance(rebuilt);
    if (Arrays.deepEquals(transactions.toArray(), rebuilt.toArray())) return;

    int selected = table.getSelectedRow();
    Object keep = selected > -1 && selected < transactions.size() ? transactions.get(selected)[tid] : null;
    quiet = true;
    try {
      transactions.clear();
      transactions.addAll(rebuilt);
      incomeBalance = inOut[0];
      outgoingBalance = inOut[1];
      TableQueryBar.getInstance().setInboxLabel(incomeBalance.toString());
      TableQueryBar.getInstance().setOutboxLabel(outgoingBalance.toString());
      fireTableDataChanged();
      if (keep != null)
        for (int i = 0; i < transactions.size(); i++)
          if (keep.equals(transactions.get(i)[tid])) {
            table.setRowSelectionInterval(i, i);
            table.scrollRectToVisible(table.getCellRect(i, 0, true));
            break;
          }
    } finally {
      quiet = false;
    }
  }

  /** The rows a re-read would give this transaction in the viewed account or category, balances blank. */
  private List<Object[]> rowsFor(Transaction t) {
    boolean account = thing instanceof Hierarchy.AccountTotal;   // before CategoryTotal: it is one
    Integer accountId = account ? ((Hierarchy.AccountTotal)thing).account.getId() : null;
    Integer categoryId = account ? null : ((Hierarchy.CategoryTotal)thing).id;

    List<Allocation> in = new ArrayList<Allocation>();
    for (Allocation a : t.getAllocations()) {
      if (a.getId() == null || a.getCategory() == null) continue;   // not saved: not in the database yet
      if (account ? a.getCategory().getAccount() != null && accountId.equals(a.getCategory().getAccount().getId())
                  : categoryId.equals(a.getCategory().getId()))
        in.add(a);
    }

    Boolean reconciled = Boolean.TRUE.equals(t.getReconciled());
    String entity = t.getEntity() == null ? "" : t.getEntity();
    String description = t.getDescription() == null ? "" : t.getDescription();
    List<Object[]> rows = new ArrayList<Object[]>();
    if (in.isEmpty()) return rows;

    if (account) {
      Money net = Money.ZERO;
      for (Allocation a : in) if (a.getAmount() != null) net = net.add(a.getAmount());
      rows.add(new Object[]{reconciled, t.getDate(), net, null, null, entity, description, t.getId(), null});
    }
    else {
      in.sort(Comparator.comparing((Allocation a) -> a.getStamp(), Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Allocation::getId));
      for (Allocation a : in)
        rows.add(new Object[]{reconciled, t.getDate(), a.getAmount() == null ? Money.ZERO : a.getAmount(),
                              null, null, entity, description, t.getId(), a.getId()});
    }
    return rows;
  }

  /**
   * Fill in the running Balance and Reconciled columns from the beginning
   * balances, as the fill does row by row, and total what came in and went out.
   *
   * @return {incoming, outgoing}, both positive
   */
  private Money[] rebalance(List<Object[]> rows) {
    Money balance = beginningBalance, reconciled = beginningReconciledBalance;
    Money in = Money.ZERO, out = Money.ZERO;
    for (Object[] row : rows) {
      Money amount = (Money)row[COLUMN.Amount.ordinal()];
      balance = balance.add(amount);
      if (Boolean.TRUE.equals(row[COLUMN.C.ordinal()])) reconciled = reconciled.add(amount);
      row[COLUMN.Balance.ordinal()] = new Money(balance);
      row[COLUMN.Reconciled.ordinal()] = new Money(reconciled);
      if (amount.compareTo(Money.ZERO) > 0) in = in.add(amount);
      else if (amount.compareTo(Money.ZERO) < 0) out = out.add(amount.abs());
    }
    return new Money[]{in, out};
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
          reconciled = reconciled.add((Money)transactions.get(x)[COLUMN.Amount.ordinal()]);
        transactions.get(x)[COLUMN.Reconciled.ordinal()] = new Money(reconciled);
        fireTableCellUpdated(x, COLUMN.Reconciled.ordinal());
      }

      // update the Transaction -- telling the form first, so a save it makes of
      // this transaction meanwhile waits for the new stamp rather than racing it
      final Integer writing = (Integer)transactions.get(row)[COLUMN.TransactionID.ordinal()];
      net.lump.envelope.client.ui.MainFrame.getInstance().getTransactionForm().reconciledWriteStarting(writing);
      ThreadPool.getInstance().execute(new StatusRunnable(
          MessageFormat.format("{0} {1} {2}", ((Boolean)value) ? Strings.get("reconciling") : Strings.get("unreconciling"),
              Strings.get("transaction").toLowerCase(), transactions.get(row)[COLUMN.TransactionID.ordinal()])) {

        public void run() {
          try {
            final Integer id = (Integer)transactions.get(row)[COLUMN.TransactionID.ordinal()];
            final java.sql.Timestamp stamp = new TransactionPortal().updateReconciled(id, (Boolean)value);
            // The write moved the transaction's stamp.  The form may hold this
            // transaction -- clicking the box selects the row, which opens it
            // there -- and its next save would be refused as stale on the old one.
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                net.lump.envelope.client.ui.MainFrame.getInstance().getTransactionForm()
                    .noteReconciled(id, (Boolean)value, stamp);
                net.lump.envelope.client.ui.MainFrame.getInstance().getTransactionForm().reconciledWriteDone(id);
              }
            });
          } catch (Exception e) {
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                net.lump.envelope.client.ui.MainFrame.getInstance().getTransactionForm().reconciledWriteDone(writing);
              }
            });
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
