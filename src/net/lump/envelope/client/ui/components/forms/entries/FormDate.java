package net.lump.envelope.client.ui.components.forms.entries;

import com.toedter.calendar.JDateChooser;
import net.lump.envelope.shared.entity.Transaction;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.Date;

/**
 * @author troy
 * @version $Id$
 */
public class FormDate extends FormEntry<JDateChooser, Transaction, Date> {

  Transaction transaction;
  JDateChooser jDateChooser;
  Runnable saveOrUpdate;

  public FormDate(JDateChooser c, Transaction t, final Runnable saveOrUpdate) {
    this.jDateChooser = c;
    this.transaction = t;
    this.saveOrUpdate = saveOrUpdate;

    addDataChangeListener(getDataChangeHandler());
  }

  @Override public Runnable getSaveOrUpdate() {
    return saveOrUpdate;
  }

  @Override public void addDataChangeListener(final Runnable dataChange) {
    jDateChooser.getDateEditor().addPropertyChangeListener(
        new PropertyChangeListener() {
          public void propertyChange(PropertyChangeEvent e) {
            if ("date".equals(e.getPropertyName()))
              dataChange.run();
          }
        }
    );
  }

  public void setTransaction(Transaction t) {
    transaction = t;
  }

  public boolean hasValidInput() {
    return jDateChooser.getDate() != null;
  }

  public JDateChooser getComponent() {
    return jDateChooser;
  }

  public Transaction getEntity() {
    return transaction;
  }

  public Date getValue() {
    return new java.sql.Date(jDateChooser.getDate().getTime());
  }

  public Date getState() {
    return transaction.getDate();
  }

  public boolean saveState() {
    if (transaction != null && getValue() != null) {
      if (getValue().equals(getState())) return false;
      transaction.setDate(getValue());
      return true;
    }
    return false;
  }
}
