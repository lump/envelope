package net.lump.envelope.client.ui.components.forms.transaction;

import net.lump.envelope.client.ui.components.MoneyTextField;
import net.lump.envelope.client.ui.defs.Colors;
import net.lump.lib.Money;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

abstract public class ChangeableMoneyTextField extends Changeable<MoneyTextField, Money> {
  MoneyTextField field;

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

  public ChangeableMoneyTextField(MoneyTextField f) {
    this.field = f;
    setDirtyDelay(500);
    addDataChangeListener();
  }

  @Override void addDataChangeListener() {
    field.addFocusListener(focusListener);
    field.addKeyListener(keyListener);
  }

  @Override void removeDataChangeListener() {
    field.removeFocusListener(focusListener);
    field.removeKeyListener(keyListener);
  }

  @Override public boolean hasValidInput() {
    try {
      new Money(field.getText());
      field.setBackground(UIManager.getLookAndFeelDefaults().getColor("TextPane.background"));
      field.setForeground(UIManager.getLookAndFeelDefaults().getColor("TextPane.foreground"));
      return true;
    }
    catch(NumberFormatException pe) {
      field.setBackground(Colors.getColor("light_red"));
      return false;
    }
  }

  @Override public MoneyTextField getComponent() {
    return this.field;
  }

  @Override public Money getValue() {
    return hasValidInput() ? new Money(this.field.getText()) : null;
  }
}
