package net.lump.envelope.client.ui.components.forms.transaction;

import javax.swing.*;

public class ChangeableComboBox extends Changeable<JComboBox, String>{
  private jComboBox;

  public ChangeableComboBox(JComboBox c, final Runnable saveOrUpdate) {
    this.jComboBox = c;
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

  public String getState() {
    return getTransaction().getTransfer();
  }

  public boolean saveState() {
    if (getTransaction() != null && getValue() != null) {
      if (getValue().equals(getState())) return false;
      getTransaction().setDate(getValue());
      return true;
    }
    return false;
  }
}
