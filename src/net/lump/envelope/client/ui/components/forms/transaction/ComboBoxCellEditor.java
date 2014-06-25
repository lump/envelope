package net.lump.envelope.client.ui.components.forms.transaction;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.PlainDocument;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.util.EventObject;

public class ComboBoxCellEditor
    extends AbstractCellEditor implements ActionListener, TableCellEditor, Serializable {

  private JComboBox comboBox;

  public ComboBoxCellEditor(final JComboBox comboBox) {
    this.comboBox = comboBox;
    this.comboBox.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);
    // hitting enter in the combo box should stop cellediting (see below)
    this.comboBox.addActionListener(this);
    // remove the editor's border - the cell itself already has one
    ((JComponent) comboBox.getEditor().getEditorComponent()).setBorder(null);

    addAutoStuff();
  }

  public InputMap getInputMap(int condition) {
    return comboBox.getInputMap(condition);
  }

  public ActionMap getActionMap() {
    return comboBox.getActionMap();
  }

  void grabFocus() {
    comboBox.grabFocus();
  }

  JComboBox getComboBox() {
    return comboBox;
  }

  private void setValue(Object value) {
    comboBox.setSelectedItem(value);
    this.selectSelectedValue();
  }

  // Implementing ActionListener
  public void actionPerformed(java.awt.event.ActionEvent e) {
    // Selecting an item results in an actioncommand "comboBoxChanged".
    // We should ignore these ones.

    // Hitting enter results in an actioncommand "comboBoxEdited"
    if(e.getActionCommand().equals("comboBoxEdited")) {
      stopCellEditing();
    }
  }

  // Implementing CellEditor
  public Object getCellEditorValue() {
    return comboBox.getSelectedItem();
  }

  public boolean stopCellEditing() {
    if (comboBox.isEditable()) {
      // Notify the combo box that editing has stopped (e.g. User pressed F2)
      comboBox.actionPerformed(new ActionEvent(this, 0, ""));
    }
    fireEditingStopped();
    return true;
  }

  public java.awt.Component getTableCellEditorComponent(javax.swing.JTable table, Object value, boolean isSelected, int row, int column) {
    setValue(value);
    return comboBox;
  }


  ComboBoxModel model;
  JTextComponent editor;
  // flag to indicate if setSelectedItem has been called
  // subsequent calls to remove/insert String should be ignored
  boolean selecting=false;
  boolean hidePopupOnFocusLoss;
  boolean hitBackspace=false;
  boolean hitBackspaceOnSelection;

  MyPlainDocument plainDocument = new MyPlainDocument();

  ActionListener highlightListener = new ActionListener() {
    public void actionPerformed(ActionEvent e) {
      if (!selecting) plainDocument.highlightCompletedText(0);
    }
  };

  PropertyChangeListener configureModelPropertyListener = new PropertyChangeListener() {
    public void propertyChange(PropertyChangeEvent e) {
      if (e.getPropertyName().equals("editor")) configureEditor((ComboBoxEditor)e.getNewValue());
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
    selectSelectedValue();
  }

  void selectSelectedValue() {
    Object selected = comboBox.getSelectedItem();
    if (selected!=null) plainDocument.setText(selected.toString());
    plainDocument.setText(comboBox.getEditor().getItem().toString());
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
  class MyPlainDocument extends PlainDocument {
    boolean strict = false;

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

      if (strict) {
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

  public boolean shouldSelectCell(EventObject anEvent) {
    return true;
  }

  public boolean isCellEditable(EventObject anEvent) {
    return true;
  }
}