package net.lump.envelope.client.ui.components.forms.transaction;

import net.lump.envelope.shared.entity.Transaction;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class FormDescription extends FormEntry<JTextField, Transaction, String> {
  JTextField field;
  Transaction transaction;
  Runnable saverOrUpdate;

  public FormDescription(JTextField f, Transaction t, Runnable r) {
    this.field = f;
    this.transaction = t;
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

  public void setTransaction(Transaction t) {
    transaction = t;
  }

  @Override public boolean hasValidInput() {
    return true;
  }

  @Override public JTextField getComponent() {
    return this.field;
  }

  @Override public Transaction getEntity() {
    return this.transaction;
  }

  @Override public String getValue() {
    return this.field.getText();
  }

  @Override public String getState() {
    return transaction.getDescription();
  }

  @Override public boolean saveState() {
    if (transaction != null && getValue() != null) {
      if (getValue().equals(getState())) return false;
      transaction.setDescription(getValue());
      return true;
    }
    return false;
  }
}
