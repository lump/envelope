package net.lump.envelope.client.ui.components.forms.transaction;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

abstract public class ChangeableComboBox extends Changeable<JComboBox<String>, String>{
  private JComboBox<String> jComboBox;

  final ItemListener itemListener = new ItemListener() {
    public void itemStateChanged(ItemEvent e) {
      handleDataChange();
    }
  };

  public ChangeableComboBox(JComboBox c) {
    this.jComboBox = c;
    addDataChangeListener();
  }

  public boolean hasValidInput() {
    return jComboBox.getEditor().getItem() != null;
  }

  @Override void addDataChangeListener() {
    jComboBox.addItemListener(itemListener);
  }

  @Override void removeDataChangeListener() {
    jComboBox.removeItemListener(itemListener);
  }

  public JComboBox getComponent() {
    return jComboBox;
  }

  public String getValue() {
    return (String)jComboBox.getSelectedItem();
  }
/*

  */
}
