package net.lump.envelope.client.ui.components;

import net.lump.lib.Money;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/** This is a JTextField with formatting for Money. */
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
          i.setText(new Money(i.getText()).toString());
          return true;
        }
        catch (Exception e) {
          return false;
        }
      }
    });

    Action emptyAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        System.out.println("e = " + e);
        MoneyTextField.this.getParent().getKeyListeners();
      }
    };
    getInputMap().put(KeyStroke.getKeyStroke("DOWN"), emptyAction);
    getInputMap().put(KeyStroke.getKeyStroke("UP"), emptyAction);


    addKeyListener(new KeyListener() {
      public void keyTyped(KeyEvent e) {
        if (!String.valueOf(e.getKeyChar()).matches("[0-9\\.\\(\\)\\$\\,\\-\b]"))
          e.consume();
      }

      public void keyPressed(KeyEvent e) { }

      public void keyReleased(KeyEvent e) { }
    });

    addFocusListener(new FocusListener() {
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
