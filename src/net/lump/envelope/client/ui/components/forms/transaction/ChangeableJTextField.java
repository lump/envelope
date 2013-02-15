package net.lump.envelope.client.ui.components.forms.transaction;

import net.lump.envelope.shared.entity.Transaction;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class ChangeableJTextField extends Changeable<JTextField, String> {
  JTextField field;
  Runnable saverOrUpdate;

  public ChangeableJTextField(JTextField f, Runnable r) {
    this.field = f;
    this.saverOrUpdate = r;
    setDirtyDelay(500);

    addDataChangeListener(getDataChangeHandler());
  }

  @Override Runnable getSaveOrUpdate() {
    return saverOrUpdate;
  }

  @Override void addDataChangeListener(final Runnable dataChange) {
    field.addFocusListener(new FocusListener() {
      public void focusGained(FocusEvent e) {}
      public void focusLost(FocusEvent e) {
        dataChange.run();
      }
    });

    field.addKeyListener(new KeyListener() {
      public void keyTyped(KeyEvent e) {
        dataChange.run();
      }
      public void keyPressed(KeyEvent e) { }
      public void keyReleased(KeyEvent e) { }
    });
  }

  @Override public boolean hasValidInput() {
    return true;
  }

  @Override public JTextField getComponent() {
    return this.field;
  }

  @Override public String getValue() {
    return this.field.getText();
  }

  @Override public String getState() {
    return getTransaction().getDescription();
  }

  @Override public boolean saveState() {
    if (getTransaction() != null && getValue() != null) {
      if (getValue().equals(getState())) return false;
      getTransaction().setDescription(getValue());
      return true;
    }
    return false;
  }
}
