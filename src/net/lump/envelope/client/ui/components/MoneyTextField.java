package us.lump.envelope.client.ui.components;

import us.lump.envelope.client.ui.defs.Colors;
import us.lump.lib.Money;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.FocusListener;
import java.awt.event.FocusEvent;
import java.awt.event.KeyListener;
import java.awt.*;

/**
 * This is a JTextField with formatting for Money.
 * @version: $Id: MoneyTextField.java,v 1.1 2008/07/21 21:59:18 troy Test $
 */
public class MoneyTextField extends JTextField {
  public MoneyTextField() {
    this(null);
  }

  public MoneyTextField(String value) {
    super(value, 15);

    setHorizontalAlignment(JTextField.RIGHT);

    setInputVerifier(new InputVerifier() {
      public boolean verify(JComponent input) {
        JTextField i = (JTextField)input;
        try {
          i.setText(new Money(i.getText()).toFormattedString());
          return true;
        }
        catch (Exception e) {
          i.setForeground(Colors.getColor("red"));
          i.setBackground(Colors.getColor("light_red"));
          return false;
        }
      }
    });

    addKeyListener(new KeyListener(){
      public void keyTyped(KeyEvent e) {
        if (!String.valueOf(e.getKeyChar()).matches("[0-9\\.\\(\\)\\$\\,\\-]"))
          e.consume();
      }
      public void keyPressed(KeyEvent e) { }
      public void keyReleased(KeyEvent e) { }
    });

    addFocusListener(new FocusListener(){
      public void focusGained(FocusEvent e) {
        ((JTextField)e.getSource()).selectAll();
      }
      public void focusLost(FocusEvent e) {
        ((Component)e.getSource()).setBackground(
            UIManager.getLookAndFeelDefaults().getColor("TextPane.background"));
        ((Component)e.getSource()).setForeground(
            UIManager.getLookAndFeelDefaults().getColor("TextPane.foreground"));
      }
    });
  }
}
