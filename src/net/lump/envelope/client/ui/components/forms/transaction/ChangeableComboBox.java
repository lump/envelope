package net.lump.envelope.client.ui.components.forms.transaction;

import javax.swing.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.PlainDocument;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

abstract public class ChangeableComboBox extends Changeable<JComboBox<String>, String>{
  private JComboBox<String> comboBox;

  final ItemListener itemListener = new ItemListener() {
    public void itemStateChanged(ItemEvent e) {
      handleDataChange();
    }
  };

  public ChangeableComboBox(JComboBox<String> c, boolean autoComplete) {
    this.comboBox = c;
    addDataChangeListener();
    if (autoComplete) addAutoStuff();
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

  public String getValue() {
    return (String)comboBox.getSelectedItem();
  }

  ComboBoxModel model;
  JTextComponent editor;
  // flag to indicate if setSelectedItem has been called
  // subsequent calls to remove/insertString should be ignored
  boolean selecting=false;
  boolean hidePopupOnFocusLoss;
  boolean hitBackspace=false;
  boolean hitBackspaceOnSelection;

  class MyPlainDocument extends PlainDocument {

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
          comboBox.getToolkit().beep(); // when available use: UIManager.getLookAndFeel().provideErrorFeedback(comboBox);
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

      boolean listContainsSelectedItem = true;
      if (item == null) {
        // no item matches => use the current input as selected item
        item=getText(0, getLength());
        listContainsSelectedItem=false;
      }
      setText(item.toString());
      // select the completed part
      if (listContainsSelectedItem) highlightCompletedText(offs+str.length());

      /*
      if (item != null) {
        setSelectedItem(item);
      } else {
        // keep old item selected if there is no match
        item = comboBox.getSelectedItem();
        // imitate no insert (later on offs will be incremented by str.length(): selection won't move forward)
        offs = offs-str.length();
        // provide feedback to the user that his input has been received but can not be accepted
        comboBox.getToolkit().beep(); // when available use: UIManager.getLookAndFeel().provideErrorFeedback(comboBox);
      }
      setText(item.toString());
      // select the completed part
      highlightCompletedText(offs+str.length());
      */
    }


    public void setText(String text) {
      try {
        // remove all text and insert the completed string
        super.remove(0, getLength());
        super.insertString(0, text, null);
      } catch (BadLocationException e) {
        throw new RuntimeException(e.toString());
      }
    }

    public void highlightCompletedText(int start) {
      System.out.println("start: " + start + " length: " + getLength());
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

  MyPlainDocument plainDocument = new MyPlainDocument();


  ActionListener highlightListener = new ActionListener() {
    public void actionPerformed(ActionEvent e) {
      if (!selecting) plainDocument.highlightCompletedText(0);
    }
  };

  PropertyChangeListener configureModelPropertyListener = new PropertyChangeListener() {
    public void propertyChange(PropertyChangeEvent e) {
      if (e.getPropertyName().equals("editor")) configureEditor((ComboBoxEditor) e.getNewValue());
      if (e.getPropertyName().equals("model")) model = (ComboBoxModel) e.getNewValue();
    }
  };

  KeyAdapter editorKeyListener = new KeyAdapter() {
    public void keyPressed(KeyEvent e) {
      if (comboBox.isDisplayable()) comboBox.setPopupVisible(true);
      hitBackspace=false;
      switch (e.getKeyCode()) {
        // determine if the pressed key is backspace (needed by the remove method)
        case KeyEvent.VK_BACK_SPACE : hitBackspace=true;
          hitBackspaceOnSelection=editor.getSelectionStart()!=editor.getSelectionEnd();
          break;
        // ignore delete key
        case KeyEvent.VK_DELETE : e.consume();
          comboBox.getToolkit().beep();
          break;
      }
    }
  };

  FocusAdapter editorFocusListener = new FocusAdapter() {
    public void focusGained(FocusEvent e) {
      plainDocument.highlightCompletedText(0);
    }
    public void focusLost(FocusEvent e) {
      // Workaround for Bug 5100422 - Hide Popup on focus loss
      if (hidePopupOnFocusLoss) comboBox.setPopupVisible(false);
    }
  };


  void addAutoStuff() {
    model = comboBox.getModel();

    comboBox.removeActionListener(highlightListener);
    comboBox.addActionListener(highlightListener);

    comboBox.removePropertyChangeListener(configureModelPropertyListener);
    comboBox.addPropertyChangeListener(configureModelPropertyListener);

    // Bug 5100422 on Java 1.5: Editable JComboBox won't hide popup when tabbing out
    hidePopupOnFocusLoss=System.getProperty("java.version").startsWith("1.5");
    // Highlight whole text when gaining focus
    configureEditor(comboBox.getEditor());

    // Handle initially selected object
    Object selected = comboBox.getSelectedItem();
    if (selected!=null) plainDocument.setText(selected.toString());
    plainDocument.setText((String)comboBox.getEditor().getItem());
    plainDocument.highlightCompletedText(0);
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
      editor.setDocument(plainDocument);
    }
  }

}
