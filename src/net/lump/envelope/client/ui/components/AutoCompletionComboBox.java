package net.lump.envelope.client.ui.components;

import javax.swing.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.PlainDocument;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class AutoCompletionComboBox<E> extends JComboBox {
  boolean strict = true;
  AutoCompletion autoCompletion;

  public AutoCompletionComboBox() {
    super();
    AutoCompletionComboBox.this.setEditable(true);
    autoCompletion  = new AutoCompletion();
  }

  public AutoCompletionComboBox(E[] items) {
    this();
    setModel(new DefaultComboBoxModel<E>(items));
  }

  public AutoCompletionComboBox(boolean strict) {
    this();
    this.strict = strict;
  }

  private static void createAndShowGUI() {
    // the combo box (add/modify items if you like to)
    final AutoCompletionComboBox comboBox =
        new AutoCompletionComboBox<String>(new String[] {"Ester", "Jordi", "Jordina", "Jorge", "Sergi"});
    comboBox.strict = false;
    comboBox.removeAllItems();
    for (String i : new String[] {
        "A",
        "a",
        "aa",
        "aal",
        "aalii",
        "aam",
        "Aani",
        "aardvark",
        "aardwolf",
        "Aaron",
        "Aaronic",
        "Aaronical",
        "Aaronite",
        "Aaronitic",
        "Aaru",
        "Ab",
        "aba",
        "Ababdeh",
        "Ababua",
        "abac",
        "abaca",
        "abacate",
        "abacay",
        "abacinate",
        "abacination",
        "abaciscus",
        "abacist",
        "aback",
        "abactinal",
        "abactinally",
        "abaction",
        "abactor",
        "abaculus",
        "abacus",
        "Abadite",
        "abaff",
        "abaft",
        "abaisance",
        "abaiser",
        "abaissed",
        "abalienate",
        "abalienation",
        "abalone",
        "Abama",
        "abampere",
        "abandon",
        "abandonable",
        "abandoned",
        "abandonedly",
        "abandonee",
        "abandoner",
        "abandonment",
        "Abanic",
        "Abantes",
        "abaptiston",
        "Abarambo",
        "Abaris",
        "abarthrosis",
        "abarticular",
        "abarticulation",
        "abas",
        "abase",
        "abased",
        "abasedly",
        "abasedness",
        "abasement",
        "abaser",
        "Abasgi",
        "abash",
        "abashed",
        "abashedly",
        "abashedness",
        "abashless",
        "abashlessly",
        "abashment",
        "abasia",
        "abasic",
        "abask",
        "Abassin",
        "abastardize",
        "abatable",
        "abate",
        "abatement",
        "abater",
        "abatis",
        "abatised",
        "abaton",
        "abator",
        "abattoir",
        "Abatua",
        "abature",
        "abave",
        "abaxial",
        "abaxile",
        "abaze",
        "abb",
        "Abba",
        "abbacomes",
        "abbacy",
        "Abbadide",
    }) {
      comboBox.addItem(i);
    }

    // create and show a window containing the combo box
    final JFrame frame = new JFrame();
    frame.setDefaultCloseOperation(3);
    frame.getContentPane().add(comboBox);
    frame.pack(); frame.setVisible(true);
  }


  public static void main(String[] args) {
    javax.swing.SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        createAndShowGUI();
      }
    });
  }

  public class AutoCompletion extends PlainDocument {
    ComboBoxModel model;
    JTextComponent editor;
    // flag to indicate if setSelectedItem has been called
    // subsequent calls to remove/insertString should be ignored
    boolean selecting=false;
    boolean hidePopupOnFocusLoss;
    boolean hitBackspace=false;
    boolean hitBackspaceOnSelection;

    KeyListener editorKeyListener;
    FocusListener editorFocusListener;

    public AutoCompletion() {

      model = AutoCompletionComboBox.this.getModel();

      AutoCompletionComboBox.this.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (!selecting) highlightCompletedText(0);
        }
      });
      AutoCompletionComboBox.this.addPropertyChangeListener(new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent e) {
          if (e.getPropertyName().equals("editor")) configureEditor((ComboBoxEditor) e.getNewValue());
          if (e.getPropertyName().equals("model")) model = (ComboBoxModel) e.getNewValue();
        }
      });
      editorKeyListener = new KeyAdapter() {
        public void keyPressed(KeyEvent e) {
          if (AutoCompletionComboBox.this.isDisplayable()) AutoCompletionComboBox.this.setPopupVisible(true);
          hitBackspace=false;
          switch (e.getKeyCode()) {
            // determine if the pressed key is backspace (needed by the remove method)
            case KeyEvent.VK_BACK_SPACE : hitBackspace=true;
              hitBackspaceOnSelection=editor.getSelectionStart()!=editor.getSelectionEnd();
              break;
            // ignore delete key
            case KeyEvent.VK_DELETE : e.consume();
              AutoCompletionComboBox.this.getToolkit().beep();
              break;
          }
        }
      };
      // Bug 5100422 on Java 1.5: Editable JComboBox won't hide popup when tabbing out
      hidePopupOnFocusLoss=System.getProperty("java.version").startsWith("1.5");
      // Highlight whole text when gaining focus
      editorFocusListener = new FocusAdapter() {
        public void focusGained(FocusEvent e) {
          highlightCompletedText(0);
        }
        public void focusLost(FocusEvent e) {
          // Workaround for Bug 5100422 - Hide Popup on focus loss
          if (hidePopupOnFocusLoss) AutoCompletionComboBox.this.setPopupVisible(false);
        }
      };
      configureEditor(AutoCompletionComboBox.this.getEditor());
      // Handle initially selected object
      Object selected = AutoCompletionComboBox.this.getSelectedItem();
      if (selected!=null) setText(selected.toString());
      highlightCompletedText(0);
    }

    void configureEditor(ComboBoxEditor newEditor) {
      if (editor != null) {
        editor.removeKeyListener(editorKeyListener);
        editor.removeFocusListener(editorFocusListener);
      }

      if (newEditor != null) {
        editor = (JTextComponent) newEditor.getEditorComponent();
        editor.removeKeyListener(editorKeyListener);
        editor.removeFocusListener(editorFocusListener);
        editor.addKeyListener(editorKeyListener);
        editor.addFocusListener(editorFocusListener);
        editor.setDocument(this);
      }
    }

    public void remove(int offs, int len) throws BadLocationException {
      // return immediately when selecting an item
      if (selecting) return;
      if (hitBackspace) {
        // user hit backspace => move the selection backwards
        // old item keeps being selected
        if (offs>0) {
          if (hitBackspaceOnSelection) offs--;
        } else {
          // User hit backspace with the cursor positioned on the start => beep
          AutoCompletionComboBox.this.getToolkit().beep(); // when available use: UIManager.getLookAndFeel().provideErrorFeedback(comboBox);
        }
        highlightCompletedText(offs);
      } else {
        super.remove(offs, len);
      }
    }

    public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
      // return immediately when selecting an item
      if (selecting) return;
      // insert the string into the document
      super.insertString(offs, str, a);
      // lookup and select a matching item
      Object item = lookupItem(getText(0, getLength()));

      if (strict) {
        if (item != null) {
          setSelectedItem(item);
        } else {
          // keep old item selected if there is no match
          item = AutoCompletionComboBox.this.getSelectedItem();
          // imitate no insert (later on offs will be incremented by str.length(): selection won't move forward)
          offs = offs-str.length();
          // provide feedback to the user that his input has been received but can not be accepted
          AutoCompletionComboBox.this.getToolkit().beep(); // when available use: UIManager.getLookAndFeel().provideErrorFeedback(comboBox);
        }
        setText(item.toString());
        // select the completed part
        highlightCompletedText(offs+str.length());
      }
      else {
        boolean listContainsSelectedItem = true;
        if (item == null) {
          // no item matches => use the current input as selected item
          item=getText(0, getLength());
          listContainsSelectedItem=false;
        }
        setText(item.toString());
        // select the completed part
        if (listContainsSelectedItem) highlightCompletedText(offs+str.length());
      }
    }

    private void setText(String text) {
      try {
        // remove all text and insert the completed string
        super.remove(0, getLength());
        super.insertString(0, text, null);
      } catch (BadLocationException e) {
        throw new RuntimeException(e.toString());
      }
    }

    public void highlightCompletedText(int start) {
//      System.out.println("start: " + start + " length: " + getLength());
      editor.setCaretPosition(editor.getText().length());
      editor.moveCaretPosition(start);
    }

    private void setSelectedItem(Object item) {
      selecting = true;
      model.setSelectedItem(item);
      selecting = false;
    }

    private Object lookupItem(String pattern) {
      Object selectedItem = model.getSelectedItem();
      // only search for a different item if the currently selected does not match
      if (selectedItem != null && startsWithIgnoreCase(selectedItem.toString(), pattern)) {
        return selectedItem;
      } else {
        // iterate over all items
        for (int i=0, n=model.getSize(); i < n; i++) {
          Object currentItem = model.getElementAt(i);
          // current item starts with the pattern?
          if (currentItem != null && startsWithIgnoreCase(currentItem.toString(), pattern)) {
            return currentItem;
          }
        }
      }
      // no item starts with the pattern => return null
      return null;
    }

    // checks if str1 starts with str2 - ignores case
    private boolean startsWithIgnoreCase(String str1, String str2) {
      return str1.toUpperCase().startsWith(str2.toUpperCase());
    }


  }

}