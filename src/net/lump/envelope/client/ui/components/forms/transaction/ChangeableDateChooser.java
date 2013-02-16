package net.lump.envelope.client.ui.components.forms.transaction;

import com.toedter.calendar.JDateChooser;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.Date;

/**
 * @author troy
 * @version $Id$
 */
abstract public class ChangeableDateChooser extends Changeable<JDateChooser, Date> {

  JDateChooser jDateChooser;

  final PropertyChangeListener propertyChangeListener = new PropertyChangeListener() {
    public void propertyChange(PropertyChangeEvent e) {
      if ("date".equals(e.getPropertyName()))
        handleDataChange();
    }
  };

  public ChangeableDateChooser(JDateChooser c) {
    this.jDateChooser = c;
    addDataChangeListener();
  }


  public boolean hasValidInput() {
    return jDateChooser.getDate() != null;
  }

  /** Assigns the provided Runnable to the appropriate listeners for change monitoring. */
  @Override void addDataChangeListener() {
    jDateChooser.getDateEditor().addPropertyChangeListener(propertyChangeListener);
  }

  /** Removes the data change listner. */
  @Override void removeDataChangeListener() {
    jDateChooser.getDateEditor().removePropertyChangeListener(propertyChangeListener);
  }

  public JDateChooser getComponent() {
    return jDateChooser;
  }

  public Date getValue() {
    return new java.sql.Date(jDateChooser.getDate().getTime());
  }

  /*
  public Date getState() {
    return getTransaction().getDate();
  }

  public boolean saveState() {
    if (getTransaction() != null && getValue() != null) {
      if (getValue().equals(getState())) return false;
      getTransaction().setDate(getValue());
      return true;
    }
    return false;
  }
  */
}
