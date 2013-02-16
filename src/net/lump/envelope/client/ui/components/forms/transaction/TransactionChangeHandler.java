package net.lump.envelope.client.ui.components.forms.transaction;

import net.lump.envelope.client.portal.HibernatePortal;
import net.lump.envelope.client.thread.StatusRunnable;
import net.lump.envelope.client.thread.ThreadPool;
import net.lump.envelope.shared.entity.Allocation;
import net.lump.envelope.shared.entity.Transaction;
import net.lump.envelope.shared.exception.AbortException;
import net.lump.lib.Money;
import net.lump.lib.util.ObjectUtil;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.sql.Date;
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
  private TransactionForm form;

  Runnable saveOrUpdate = new Runnable() {
    public void run() {
      sendChanges();
    }
  };

  private ChangeableDateChooser changeableDate;
  private ChangeableComboBox changeableEntity;
  private ChangeableJTextField changeableDescription;
  private ChangeableMoneyTextField changeableAmount;

  public TransactionChangeHandler(Transaction t, TransactionForm tf) throws InvocationTargetException, InterruptedException {

    Collections.sort(t.getAllocations(), new Comparator<Allocation>() {
      public int compare(Allocation one, Allocation other) {
        return one.getCategory().getName().compareTo(other.getCategory().getName());
      }
    });

    importNew(t, tf);
  }

  public void importNew(Transaction t, TransactionForm tf) throws InvocationTargetException, InterruptedException {
    form = tf;
    changeHistory.clear();
    changeHistory.push(t);
    saveAttributes(t);
    setFormData();
  }

  private void saveAttributes(Transaction t) {
    pristine = t;
    editing = ObjectUtil.deepCopy(pristine);
  }

  public Transaction getTransaction() {
    return editing;
  }

  private void setFormData() throws InvocationTargetException, InterruptedException {
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        try {
          if (changeableAmount != null) changeableAmount.removeDataChangeListener();
          if (changeableDate != null) changeableDate.removeDataChangeListener();
          if (changeableDescription != null) changeableDescription.removeDataChangeListener();
          if (changeableEntity != null) changeableEntity.removeDataChangeListener();

          form.getTableModel().setAllocations(editing.getAllocations());

          form.getAmount().setText(editing.getNetAmount().toString());
          changeableAmount = new ChangeableMoneyTextField(form.getAmount()) {
            @Override public Money getState() { return TransactionChangeHandler.this.editing.getNetAmount(); }
            @Override public boolean saveState() { return false; }
            @Override public Runnable getSaveOrUpdate() { return saveOrUpdate; }

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
          changeableEntity = new ChangeableComboBox(form.getEntity()) {
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

          form.getAmount().setEnabled(editing.getReconciled());
          form.getTransactionAllocationSplit().resetToPreferredSizes();

          if (editing.getNetAmount().doubleValue() > 0) form.setView(false);
          else form.setView(true);

        } catch (AbortException ignore) {
          ignore.printStackTrace();
        } catch (Exception ignore) {
          ignore.printStackTrace();
        }
      }
    });
  }


   private void sendChanges() {
    if (!equals(pristine)) {
      StatusRunnable r = new StatusRunnable("Updating transaction " + pristine.getId()) {
        @Override public void run() {
          HibernatePortal hp = new HibernatePortal();
          try {
            changeHistory.push(TransactionChangeHandler.this.getTransaction());
            saveAttributes(hp.saveOrUpdate(TransactionChangeHandler.this.getTransaction()));
          } catch (AbortException ignore) { }
        }
      };
      ThreadPool.getInstance().execute(r);
    }
  }
}
