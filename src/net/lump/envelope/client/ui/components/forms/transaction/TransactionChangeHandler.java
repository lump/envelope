package net.lump.envelope.client.ui.components.forms.transaction;

import net.lump.envelope.client.State;
import net.lump.envelope.client.portal.HibernatePortal;
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
import net.lump.lib.util.ObjectUtil;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.sql.Date;
import java.text.DateFormat;
import java.util.Collections;
import java.util.Comparator;

/**
 * @author troy
 * @version $Id$
 */
public class TransactionChangeHandler {

  final LimitedStack<Transaction> changeHistory = new LimitedStack<Transaction>();
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
              return new Runnable() { public void run () { updateAllocationTotalLabels(); } };
            }
          };

          form.getTransactionDate().setDate(editing.getDate());
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

  public void updateAllocationTotalLabels() {
    Money in = Money.ZERO;
    Money out = Money.ZERO;
    Money balance = Money.ZERO;

    if (editing != null) {
      in = editing.getIncomeAmount().abs();
      balance = editing.getNetAmount();
      out = editing.getDebitAmount().abs();

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
            saveAttributes(hp.saveOrUpdate(TransactionChangeHandler.this.getTransaction()));
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
    // rows that were never saved have no id yet; adding them is separate work
    if (allocation.getId() == null) return;

    StatusRunnable r = new StatusRunnable("Updating allocation " + allocation.getId()) {
      @Override public void run() {
        try {
          Allocation saved = new HibernatePortal().saveOrUpdate(allocation);
          // Take the new version stamp forward, or the next edit of this same row
          // is refused as stale.  Only the stamp is copied: keeping our own object
          // leaves the form's graph (and the table model's list) intact.
          allocation.setStamp(saved.getStamp());
          setSavedLabel();
          refreshTotals();
        } catch (AbortException e) {
          setSaveFailedLabel();
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

  private void setSavedLabel() {
    DateFormat df = DateFormat.getTimeInstance();
    form.setSaveStateLabel(Strings.get("saved.at") + " " + df.format(new java.util.Date()));
  }

  /**
   * Portal has already put a dialog in front of the user by the time an
   * AbortException arrives, but the form's own label would otherwise sit on
   * "Save Pending" and quietly claim the write is still coming.
   */
  private void setSaveFailedLabel() {
    form.setSaveStateLabel(Strings.get("save.failed"));
  }
}
