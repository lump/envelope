package net.lump.envelope.client.ui.components.forms.transaction;

import com.toedter.calendar.JDateChooser;
import net.lump.envelope.shared.entity.Transaction;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.Date;

/**
 * @author troy
 * @version $Id$
 */
public class ChangeableDateChooser extends Changeable<JDateChooser, Date> {

  JDateChooser jDateChooser;
  Runnable saveOrUpdate;

  public ChangeableDateChooser(JDateChooser c, final Runnable saveOrUpdate) {
    this.jDateChooser = c;
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

  public boolean hasValidInput() {
    return jDateChooser.getDate() != null;
  }

  public JDateChooser getComponent() {
    return jDateChooser;
  }

  public Date getValue() {
    return new java.sql.Date(jDateChooser.getDate().getTime());
  }

  public Date getState(Transaction transaction) {
    return transaction.getDate();
  }

  public boolean saveState(Transaction transaction) {
    if (transaction != null && getValue() != null) {
      if (getValue().equals(getState(transaction))) return false;
      transaction.setDate(getValue());
      return true;
    }
    return false;
  }
}
