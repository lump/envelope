package net.lump.envelope.client.ui.components.forms.transaction;

import net.lump.envelope.client.State;
import net.lump.envelope.client.portal.HibernatePortal;
import net.lump.envelope.client.portal.TransactionPortal;
import net.lump.envelope.client.thread.StatusRunnable;
import net.lump.envelope.client.thread.ThreadPool;
import net.lump.envelope.client.ui.components.Hierarchy;
import net.lump.envelope.client.ui.components.forms.table_query_bar.TableQueryBar;
import net.lump.envelope.client.ui.defs.Colors;
import net.lump.envelope.client.ui.defs.Fonts;
import net.lump.envelope.client.ui.defs.Strings;
import net.lump.envelope.shared.entity.Allocation;
import net.lump.envelope.shared.entity.Category;
import net.lump.envelope.shared.entity.Transaction;
import net.lump.envelope.shared.exception.AbortException;
import net.lump.lib.Money;
import net.lump.lib.util.Day;
import net.lump.lib.util.ObjectUtil;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.sql.Date;
import java.text.DateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.TimeZone;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * @author troy
 */
public class TransactionChangeHandler {

  final LimitedStack<Transaction> changeHistory = new LimitedStack<Transaction>();

  /**
   * Allocations with a save in flight, each mapped to whether it was edited again
   * while that save was running.  Keyed by identity, because Allocation.equals
   * compares the parent Transaction and two rows can compare equal.
   */
  private final Map<Allocation, Boolean> savesInFlight =
      new IdentityHashMap<Allocation, Boolean>();

  /**
   * Allocations whose delete is in flight.  A row stays on screen until its round
   * trip returns, so without this two quick deletes would each count the full
   * table, each believe a row would survive, and together empty the transaction.
   */
  private final Map<Allocation, Boolean> deletesInFlight =
      new IdentityHashMap<Allocation, Boolean>();

  /**
   * Allocations whose delete was asked for while their insert was still in the air.
   * Guarded by the {@link #savesInFlight} monitor, since it is that save's
   * lifecycle this hangs off.
   */
  private final Map<Allocation, Boolean> deleteWhenSaved =
      new IdentityHashMap<Allocation, Boolean>();

  private Transaction pristine;
  private Transaction editing;
  private Money amount;
  private TransactionForm form;
  Boolean isExpense = null;

  Runnable saveOrUpdate = new Runnable() {
    public void run() {
      sendChanges();
    }
  };

  private ChangeableDateChooser changeableDate;
  private ChangeableComboBox<JComboBox<String>, String> changeableEntity;
  private ChangeableJTextField changeableDescription;
  private ChangeableMoneyTextField changeableAmount;
  ChangeableComboBox<JComboBox<Category>, Category> changeableAllocationCategory;
  ChangeableMoneyTextField changeableAllocationMoney;

  public TransactionChangeHandler(Transaction t, TransactionForm tf) throws InvocationTargetException, InterruptedException {
    importNew(t, tf);
  }

  public void importNew(Transaction t, TransactionForm tf) throws InvocationTargetException, InterruptedException {
    Collections.sort(t.getAllocations(), new Comparator<Allocation>() {
      public int compare(Allocation one, Allocation other) {
        int cp = one.getCategory().getName().compareTo(other.getCategory().getName());
        return (cp == 0) ? one.getAmount().compareTo(other.getAmount())*-1 : cp;
      }
    });
    form = tf;
    changeHistory.clear();
    changeHistory.push(t);
    isExpense = null;
    saveAttributes(t);
    setFormData();
  }

  private void saveAttributes(Transaction t) {
    pristine = t;
    editing = ObjectUtil.deepCopy(pristine);
    amount = editing.getNetAmount();
  }

  public Transaction getTransaction() {
    return editing;
  }

  public Boolean isExpense() {
    if (isExpense == null && amount != null) {
      isExpense = amount.compareTo(Money.ZERO) < 0;
    }
    return isExpense;
  }

  public void setExpense(boolean expense) {
    // Commit any open allocation editor FIRST, while the view still has the
    // sign convention the value was typed under.  The amount cell shows an
    // expense unsigned and re-signs it on commit according to the model's
    // current view; the radio's click both flips that view (here, synchronously)
    // and takes focus from the editor (committing it, a moment later).  Left in
    // that order, a "25.00" typed into an expense row committed under the Income
    // view as +25.00 -- a silent sign flip -- and then raced the amount field's
    // own focus-loss save for the same row, which is the StaleObjectState the
    // user saw.  Committing here makes the flip a pure change of view.
    JTable table = form.getAllocationsTable();
    if (table != null && table.isEditing() && table.getCellEditor() != null)
      table.getCellEditor().stopCellEditing();

    isExpense = expense;
    form.getTypeExpenseRadio().setSelected(expense);
    form.getTypeIncomeRadio().setSelected(!expense);

    form.getEntityLabel().setText(expense ? Strings.get("paid.to") : Strings.get("received.from"));

    if (form.getTableModel() != null)
      form.getTableModel().setExpense(expense);

    changeableAmount.getComponent().setText(
        expense ? amount.negate().toString()
                : amount.toString());

    updateAllocationTotalLabels();
  }

  private void setFormData() throws InvocationTargetException, InterruptedException {
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        try {

          JTable table = TableQueryBar.getInstance().getTable();
          table.scrollRectToVisible(table.getCellRect(table.getSelectedRow(), 0, true));

          if (changeableAmount != null) changeableAmount.removeDataChangeListener();
          if (changeableDate != null) changeableDate.removeDataChangeListener();
          if (changeableDescription != null) changeableDescription.removeDataChangeListener();
          if (changeableEntity != null) changeableEntity.removeDataChangeListener();
          if (changeableAllocationCategory != null) changeableAllocationCategory.removeDataChangeListener();

          form.getTableModel().setAllocations(editing.getAllocations());
          form.getTableModel().setEditListener(
              new AllocationFormTableModel.EditListener() {
                public void allocationEdited(Allocation allocation) {
                  sendAllocationChange(allocation);
                }
              });
          int amountWidth = table.getFontMetrics(table.getFont()).stringWidth("$0,000,000.00");
          form.getAllocationsTable().getColumnModel().getColumn(1).setMaxWidth(amountWidth);
          form.getAllocationsTable().getColumnModel().getColumn(1).setMinWidth(amountWidth);

          form.getAmount().setEnabled(!editing.getReconciled());
          form.getAmount().setText(amount.toString());
          changeableAmount = new ChangeableMoneyTextField(form.getAmount()) {
            { super.setDirtyDelay(5); }
            @Override public Money getState() { return TransactionChangeHandler.this.editing.getNetAmount(); }
            @Override public boolean saveState() {
              if ((getValue() != null)
                  && (isExpense ? !getValue().negate().equals(amount) : !getValue().equals(amount))) {
                amount = isExpense ? getValue().negate() : getValue();
                return true;
              }
              return false;
            }
            @Override public Runnable getSaveOrUpdate() {
              return new Runnable() {
                public void run() {
                  writeAmountThroughToSingleAllocation();
                  updateAllocationTotalLabels();
                }
              };
            }
          };

          // the stored day arrives as midnight UTC; show it as that day in the user's zone
          form.getTransactionDate().setDate(Day.toView(editing.getDate(), TimeZone.getDefault()));
          changeableDate = new ChangeableDateChooser(form.getTransactionDate()){
            @Override public Date getState() { return TransactionChangeHandler.this.editing.getDate(); }
            @Override public boolean saveState() {
              if (getValue() != null) {
                if (getValue().equals(getState())) return false;
                TransactionChangeHandler.this.editing.setDate(getValue());
                return true;
              }
              return false;
            }
            @Override public Runnable getSaveOrUpdate() { return saveOrUpdate; }
          };

          try {
            form.getDescription().setDocument(new TransactionForm.LimitDocument(Transaction.class.getMethod("getDescription")));
          } catch (NoSuchMethodException ignore) {}
          form.getDescription().setText(editing.getDescription());
          changeableDescription = new ChangeableJTextField(form.getDescription()) {
            @Override public String getState() { return TransactionChangeHandler.this.editing.getDescription(); }
            @Override public boolean saveState() {
              if (getValue() != null) {
                if (getValue().equals(getState())) return false;
                TransactionChangeHandler.this.editing.setDescription(getValue());
                return true;
              }
              return false;
            }
            @Override public Runnable getSaveOrUpdate() {
              return saveOrUpdate;
            }
          };

          form.refreshEntities();
          try {
            ((JTextField)form.getEntity().getEditor().getEditorComponent())
                .setDocument(new TransactionForm.LimitDocument(Transaction.class.getMethod("getEntity")));
          } catch (NoSuchMethodException ignore) {}
          form.getEntity().setSelectedItem(editing.getEntity());
          changeableEntity = new ChangeableComboBox<JComboBox<String>, String>(form.getEntity()) {
            public String getState() { return TransactionChangeHandler.this.editing.getEntity(); }
            public boolean saveState() {
              if (getValue() != null) {
                if (getValue().equals(getState())) return false;
                TransactionChangeHandler.this.editing.setEntity(getValue());
                return true;
              }
              return false;
            }

            @Override public Runnable getSaveOrUpdate() {
              return saveOrUpdate;
            }
          };

          /*
          changeableAllocationCategory =
              new ChangeableComboBox<JComboBox<Category>, Category>(form.getCategoriesComboBox()) {
                @Override public Category getState() {
                  return null;
                }

                @Override public boolean saveState() {
                  return false;
                }

                @Override public Runnable getSaveOrUpdate() {
                  return null;
                }
              };

          changeableAllocationMoney = new ChangeableMoneyTextField(form.getAmount()) {
            { super.setDirtyDelay(5); }
            @Override public Money getState() { return TransactionChangeHandler.this.editing.getNetAmount(); }
            @Override public boolean saveState() {
              if ((getValue() != null)
                  && (isExpense ? !getValue().negate().equals(amount) : !getValue().equals(amount))) {
                amount = isExpense ? getValue().negate() : getValue();
                return true;
              }
              return false;
            }
            @Override public Runnable getSaveOrUpdate() {
              return new Runnable() { public void run () { updateAllocationTotalLabels(); } };
            }
          };
          */

          form.getTransactionAllocationSplit().setDividerLocation(0.60D);
//          form.getTransactionAllocationSplit().resetToPreferredSizes();


          isExpense = amount.compareTo(Money.ZERO) < 0;
          form.setViewIsExpense(isExpense);
          setExpense(isExpense); // this has to be run again because we might still be constructing
          updateAllocationTotalLabels();
        } catch (AbortException ignore) {
          ignore.printStackTrace();
        } catch (Exception ignore) {
          ignore.printStackTrace();
        }
      }
    });
  }

  /**
   * Put a stored day onto the date picker in the user's current zone, without
   * the picker's change listener mistaking it for an edit.
   */
  public void showDate(final java.sql.Date stored) {
    if (changeableDate == null) {
      form.getTransactionDate().setDate(Day.toView(stored, TimeZone.getDefault()));
      return;
    }
    changeableDate.removeDataChangeListener();
    form.getTransactionDate().setDate(Day.toView(stored, TimeZone.getDefault()));
    changeableDate.addDataChangeListener();
  }

  /**
   * Whether this transaction has exactly one allocation, in which case the
   * transaction amount and that allocation's amount are the same number.
   */
  private boolean isSingleAllocation() {
    return editing != null
           && editing.getAllocations() != null
           && editing.getAllocations().size() == 1;
  }

  /**
   * Editing the transaction amount with one allocation is really editing that
   * allocation, so write it through.
   *
   * <p>There is no transactions.amount column -- Transaction.getNetAmount() sums
   * the allocations, and every balance in the app is a sum over them -- so the
   * allocations are the authority and this field is a view of them.  With more
   * than one there is a real decision about where the difference goes, which is
   * not the form's to make: the amount stays a target and the imbalance indicator
   * shows the gap until the user apportions it.
   */
  private void writeAmountThroughToSingleAllocation() {
    if (!isSingleAllocation() || amount == null) return;

    Allocation only = editing.getAllocations().get(0);
    if (amount.equals(only.getAmount())) return;

    only.setAmount(amount);
    form.getTableModel().rowChanged(only);
    sendAllocationChange(only);
  }

  /**
   * The other direction: the allocation changed, so move the field to match.
   *
   * <p>Only touches the text when it actually differs and the field is not being
   * typed into, so this cannot fight the user for the caret.
   */
  private void syncAmountToBalance(Money balance) {
    amount = balance;
    if (isExpense == null || changeableAmount == null || form.getAmount().isFocusOwner()) return;

    String text = (isExpense ? balance.negate() : balance).toString();
    if (text.equals(form.getAmount().getText())) return;

    // Move the field without waking its own change listener.  This is the form
    // catching up with the allocation, not the user editing it; letting it round
    // trip would schedule a save that has nothing to save and would wipe the
    // "Saved at" message the allocation's own save just put there.
    changeableAmount.removeDataChangeListener();
    form.getAmount().setText(text);
    changeableAmount.addDataChangeListener();
  }

  public void updateAllocationTotalLabels() {
    Money in = Money.ZERO;
    Money out = Money.ZERO;
    Money balance = Money.ZERO;

    if (editing != null) {
      in = editing.getIncomeAmount().abs();
      balance = editing.getNetAmount();
      out = editing.getDebitAmount().abs();

      // With one allocation the two amounts track each other, so an imbalance is
      // not a state this form can meaningfully be in -- keep the field level with
      // the allocation instead of reporting a gap the user cannot act on.
      if (isSingleAllocation()) syncAmountToBalance(balance);

      if (changeableAmount.hasValidInput()) {
        if (balance.compareTo(amount) != 0) {
          form.getImbalanceMessagePanel().setBackground(Colors.getColor("red"));
          form.getImbalanceMessagePanel().setBorder(BorderFactory.createLineBorder(Colors.getColor("black")));
          form.getImbalanceMessageLabel().setForeground(Colors.getColor("white"));
          form.getImbalanceMessageLabel().setFont(Fonts.sans_14_bold.getFont());
          form.getImbalanceMessageLabel().setText("Imbalance: " + amount.subtract(balance));
        }
        else {
          form.getImbalanceMessagePanel().setBackground(null);
          form.getImbalanceMessagePanel().setBorder(null);
          form.getImbalanceMessageLabel().setText(null);
        }
      }
    }

    form.setInboxLabel(in.toString());
    form.setBalanceLabel(isExpense() ? balance.negate().toString() : balance.toString());
    form.setOutboxLabel(out.toString());
  }


   private void sendChanges() {
    // This compared the handler itself against a Transaction, so it was never
    // equal and every edit sent whether or not anything had changed.  Comparing
    // the edited copy to the pristine one is what was meant.  Note that this only
    // catches the transaction's own fields: Transaction.equals builds both sides
    // of its allocation comparison from this.allocations, so allocation changes
    // are invisible to it -- they come through sendAllocationChange instead.
    if (!editing.equals(pristine)) {
      StatusRunnable r = new StatusRunnable("Updating transaction " + pristine.getId()) {
        @Override public void run() {
          HibernatePortal hp = new HibernatePortal();
          try {
            changeHistory.push(TransactionChangeHandler.this.getTransaction());
            Transaction saved = hp.saveOrUpdate(TransactionChangeHandler.this.getTransaction());
            // Take the transaction's new stamp onto the graph the form is ALREADY
            // editing -- do not replace that graph.  saveAttributes() used to swap
            // `editing` for a fresh deep copy here, which orphaned every
            // Allocation the table model and the amount field were holding: their
            // later saves moved stamps on objects nothing displayed, while the
            // copies on screen kept the stamps this save returned.  The next save
            // through the screen's copy was refused as stale.  An allocation's
            // stamp is only ever moved by its own save now.
            editing.setStamp(saved.getStamp());
            pristine = ObjectUtil.deepCopy(editing);
            setSavedLabel();
          } catch (AbortException e) {
            setSaveFailedLabel();
          }
        }
      };
      ThreadPool.getInstance().execute(r);
    }
  }

  /**
   * Persist one edited Allocation.
   *
   * <p>Allocations are saved individually rather than as part of their Transaction.
   * {@code Transaction.allocations} is mapped {@code @OneToMany(mappedBy =
   * "transaction")} with no cascade -- an inverse side that owns no foreign key --
   * so {@code saveOrUpdate(Transaction)} succeeds and silently writes nothing for
   * them.  The cascade runs the other way, up from Allocation to its Transaction
   * and Category, which is why saving the Allocation on its own works.
   */
  void sendAllocationChange(final Allocation allocation) {
    // Category and amount are both NOT NULL, so a row that has not been filled in
    // cannot be written yet; it saves as soon as it has both.
    if (allocation.getCategory() == null || allocation.getAmount() == null) return;

    // One save per row at a time.  The pool is (0, MAX_VALUE) over a
    // SynchronousQueue, so every task starts at once and nothing would otherwise
    // stop two edits of a row that has no id yet from both inserting it.
    synchronized (savesInFlight) {
      if (savesInFlight.containsKey(allocation)) {
        savesInFlight.put(allocation, Boolean.TRUE);   // re-send once this lands
        return;
      }
      savesInFlight.put(allocation, Boolean.FALSE);
    }

    final boolean insert = allocation.getId() == null;
    StatusRunnable r = new StatusRunnable(insert
        ? "Adding allocation to transaction " + editing.getId()
        : "Updating allocation " + allocation.getId()) {
      @Override public void run() {
        boolean saved_ok = false;
        try {
          Allocation saved = new HibernatePortal().saveOrUpdate(allocation);
          // On an insert this is where the generated id arrives; on an update, the
          // new version stamp.  Carrying both forward is what stops the next edit
          // of this row from inserting a duplicate or being refused as stale.
          // Only these two fields are copied: keeping our own object leaves the
          // form's graph (and the table model's list) intact.
          allocation.setId(saved.getId());
          allocation.setStamp(saved.getStamp());
          saved_ok = true;
          setSavedLabel();
          refreshTotals();
        } catch (AbortException e) {
          setSaveFailedLabel();
        } finally {
          boolean editedWhileSaving;
          boolean deleteWasAsked;
          synchronized (savesInFlight) {
            editedWhileSaving = Boolean.TRUE.equals(savesInFlight.remove(allocation));
            deleteWasAsked = Boolean.TRUE.equals(deleteWhenSaved.remove(allocation));
          }
          // A delete that arrived while this save was in the air was waiting for
          // the id this save carries back.  Do that ahead of any edit: re-sending
          // a change for a row that is about to go is pointless.  It runs whether
          // or not the save worked -- if the insert failed there is no row to
          // delete and the id is still null, which is exactly the case
          // deleteAllocation already handles by just taking the row off screen.
          if (deleteWasAsked) deleteAllocation(allocation);
            // Only chase a change that arrived mid-save if this one worked.  After a
            // failure the row is out of step with the database, and re-sending the
            // same object would just fail the same way.
          else if (editedWhileSaving && saved_ok) sendAllocationChange(allocation);
        }
      }
    };
    ThreadPool.getInstance().execute(r);
  }

  /**
   * Append a new allocation to this transaction and write it out.
   *
   * <p>The row is born valid rather than blank -- both of its columns are NOT NULL
   * -- so it inserts immediately and has an id by the time the user types into it.
   * Saving the Allocation is also what carries the association: its @ManyToOne
   * cascades reach the Transaction and Category, while the Transaction's own
   * collection would carry nothing.
   */
  void addAllocation() {
    Category category = form.getTableModel().defaultCategory();
    if (category == null) return;   // nothing to copy an account from

    Allocation allocation = new Allocation();
    allocation.setTransaction(editing);
    allocation.setCategory(category);
    allocation.setAmount(Money.ZERO);

    // the model's list IS editing.getAllocations(), so this adds to both
    form.getTableModel().addEmptyRow(allocation);
    sendAllocationChange(allocation);
  }

  /**
   * Lay a named allocation preset over this transaction.
   *
   * <p>Done in one call rather than a save per row.  A preset expands to dozens of
   * allocations -- and to two of them for every auto-deduct row, the amount in and
   * the same amount straight back out -- so they should either all land or none of
   * them should.  The server sizes the percentages against the amount showing on
   * the form, which for a paycheck is the gross.
   *
   * <p>What comes back is not expected to balance.  Reconciling the preset against
   * the transaction amount, usually by putting the remainder somewhere like Stash
   * or Savings, is the user's to do; the imbalance panel shows the gap.
   */
  void applyPreset(final String presetName) {
    if (editing == null || editing.getId() == null) return;
    final Money gross = amount;

    StatusRunnable r = new StatusRunnable("Applying preset " + presetName) {
      @Override public void run() {
        try {
          Transaction applied = new TransactionPortal()
              .applyAllocationPreset(editing.getId(), presetName, gross);
          // rebuild the form on what came back rather than guessing at it locally
          importNew(applied, form);
          setSavedLabel();
          refreshTotals();
        } catch (AbortException e) {
          setSaveFailedLabel();
        } catch (InvocationTargetException e) {
          setSaveFailedLabel();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    };
    ThreadPool.getInstance().execute(r);
  }

  /**
   * Remove one allocation from this transaction.
   *
   * @param row the row index in the allocations table
   */
  void deleteAllocation(final Allocation allocation) {
    if (allocation == null) return;
    java.util.List<Allocation> rows = form.getTableModel().getAllocations();
    if (rows == null) return;

    // Read the id and the in-flight state together, under the monitor the save
    // path publishes them with.
    //
    // Taking the id alone was not enough.  The save path sets the id OUTSIDE this
    // monitor and only touches savesInFlight to register before the round trip and
    // to remove in its finally, so the monitor buys visibility of a save that has
    // already landed and never serialization with one still in the air.  A row
    // whose insert was still in flight therefore read a null id here, skipped the
    // server delete as "never written", and left the screen -- then the insert
    // landed and the database kept an allocation no view shows.  Every balance in
    // the app is a sum(amount) over allocations, so that silently wrongs the
    // account total, the category total and getNetAmount().
    //
    // Wait for the id instead: the save's finally re-enters here once it exists.
    final Integer id;
    synchronized (savesInFlight) {
      if (allocation.getId() == null && savesInFlight.containsKey(allocation)) {
        deleteWhenSaved.put(allocation, Boolean.TRUE);
        return;
      }
      id = allocation.getId();
    }

    synchronized (deletesInFlight) {
      if (deletesInFlight.containsKey(allocation)) return;   // already on its way

      // Count only rows that are not already being removed.  The server enforces
      // this too -- it must, since nothing stops another client -- but refusing
      // here saves a round trip and gives a better reason than the server's.
      int surviving = 0;
      for (Allocation a : rows) if (!deletesInFlight.containsKey(a)) surviving++;
      if (surviving <= 1) {
        JOptionPane.showMessageDialog(
            form.getTransactionFormPanel(),
            Strings.get("error.last.allocation"),
            Strings.get("error"),
            JOptionPane.ERROR_MESSAGE);
        return;
      }
      deletesInFlight.put(allocation, Boolean.TRUE);
    }

    StatusRunnable r = new StatusRunnable("Deleting allocation " + id) {
      @Override public void run() {
        try {
          // a row that was never written has nothing to delete server-side
          if (id != null) new TransactionPortal().deleteAllocation(id);
          SwingUtilities.invokeLater(new Runnable() {
            public void run() { form.getTableModel().removeRow(allocation); }
          });
          setSavedLabel();
          refreshTotals();
        } catch (AbortException e) {
          // the row is still there and still real, so put it back in play
          setSaveFailedLabel();
        } finally {
          synchronized (deletesInFlight) { deletesInFlight.remove(allocation); }
        }
      }
    };
    ThreadPool.getInstance().execute(r);
  }

  /**
   * Re-read the totals an allocation change invalidates: the form's own in/out/
   * balance labels, and the account and category balances in the tree, which are
   * sum(amount) projections over exactly the row that just moved.
   */
  private void refreshTotals() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() { updateAllocationTotalLabels(); }
    });
    try {
      // refreshTree runs itself on the thread pool and keeps the selection
      Hierarchy.getInstance().refreshTree(State.getInstance().getBudget());
    } catch (AbortException e) {
      // the tree is stale but the save itself stood; Portal already told the user
    }
  }

  /**
   * Both label helpers hop to the EDT: every caller runs on the thread pool, and
   * Swing components may only be touched from the event thread.
   */
  private void setSavedLabel() {
    DateFormat df = DateFormat.getTimeInstance();
    final String text = Strings.get("saved.at") + " " + df.format(new java.util.Date());
    SwingUtilities.invokeLater(new Runnable() {
      public void run() { form.setSaveStateLabel(text); }
    });
  }

  /**
   * Portal has already put a dialog in front of the user by the time an
   * AbortException arrives, but the form's own label would otherwise sit on
   * "Save Pending" and quietly claim the write is still coming.
   */
  private void setSaveFailedLabel() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() { form.setSaveStateLabel(Strings.get("save.failed")); }
    });
  }
}
