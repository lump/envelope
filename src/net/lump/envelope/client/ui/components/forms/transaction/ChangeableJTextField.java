package net.lump.envelope.client.ui.components.forms.transaction;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

abstract public class ChangeableJTextField extends Changeable<JTextField, String> {
  JTextField field;

  FocusListener focusListener = new FocusListener() {
    public void focusGained(FocusEvent e) {}
    public void focusLost(FocusEvent e) {
      handleDataChange();
    }
  };
  KeyListener keyListener = new KeyListener() {
    public void keyTyped(KeyEvent e) {
      handleDataChange();
    }
    public void keyPressed(KeyEvent e) { }
    public void keyReleased(KeyEvent e) { }
  };

  public ChangeableJTextField(JTextField f) {
    this.field = f;
    setDirtyDelay(500);
    addDataChangeListener();
  }

  @Override void addDataChangeListener() {
    field.addFocusListener(focusListener);
    field.addKeyListener(keyListener);
  }

  /** Removes the data change listner. */
  @Override void removeDataChangeListener() {
    field.removeFocusListener(focusListener);
    field.removeKeyListener(keyListener);
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

  /*

  */
}
