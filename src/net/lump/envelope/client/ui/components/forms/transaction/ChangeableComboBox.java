package net.lump.envelope.client.ui.components.forms.transaction;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

abstract class ChangeableComboBox<C extends JComboBox<V>, V> extends Changeable<C, V> {

  private C comboBox;

  final ItemListener itemListener = new ItemListener() {
    public void itemStateChanged(ItemEvent e) {
      handleDataChange();
    }
  };

  public ChangeableComboBox(C c) {
    this.comboBox = c;
    addDataChangeListener();
  }

  public boolean hasValidInput() {
    return comboBox.getEditor().getItem() != null;
  }

  @Override void addDataChangeListener() {
    comboBox.addItemListener(itemListener);
  }

  @Override void removeDataChangeListener() {
    comboBox.removeItemListener(itemListener);
  }

  public JComboBox getComponent() {
    return comboBox;
  }

  public V getValue() {
    //noinspection unchecked
    return (V)comboBox.getSelectedItem();
  }

}
