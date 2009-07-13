package us.lump.envelope.client.ui.components.models;

/*
 * @(#)DefaultCellEditor.java	1.53 08/02/04
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

import us.lump.envelope.client.ui.defs.Colors;
import us.lump.envelope.shared.entity.Category;
import us.lump.lib.Money;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreeCellEditor;
import java.awt.*;
import java.awt.event.*;
import java.io.Serializable;
import java.util.EventObject;

/**
 * The default editor for table and tree cells.
 * <p/>
 * <strong>Warning:</strong> Serialized objects of this class will not be compatible with future Swing releases. The current
 * serialization support is appropriate for short term storage or RMI between applications running the same version of Swing.  As of
 * 1.4, support for long term storage of all JavaBeans<sup><font size="-2">TM</font></sup> has been added to the
 * <code>java.beans</code> package. Please see {@link java.beans.XMLEncoder}.
 *
 * @author Alan Chung
 * @author Philip Milne
 * @version 1.53 02/04/08
 */

public class CellEditor extends AbstractCellEditor
    implements TableCellEditor, TreeCellEditor {

//
//  Instance Variables
//

  /** The Swing component being edited. */
  protected JComponent editorComponent;
  /** The delegate class which handles all methods sent from the <code>CellEditor</code>. */
  protected EditorDelegate delegate;
  /**
   * An integer specifying the number of clicks needed to start editing. Even if <code>clickCountToStart</code> is defined as
   * zero, it will not initiate until a click occurs.
   */
  protected int clickCountToStart = 1;

//
//  Constructors
//

  /**
   * Constructs a <code>DefaultCellEditor</code> that uses a text field.
   *
   * @param textField a <code>JTextField</code> object
   */
  public CellEditor(final JTextField textField) {
    editorComponent = textField;
    this.clickCountToStart = 1;
    delegate = new EditorDelegate() {
      public void setValue(Object value) {
        textField.setText((value != null)
                          ? value instanceof Money
                            ? ((Money)value).toFormattedString()
                            : value.toString()
                          : "");
        textField.selectAll();
        textField.setBorder(BorderFactory.createLineBorder(Colors.getColor("black")));
      }

      public Object getCellEditorValue() {
        return textField.getText();
      }
    };
    textField.addActionListener(delegate);
//    textField.addFocusListener(new FocusAdapter() {
//      public void focusGained(FocusEvent e) {
//        Component src = e.getComponent();
//        if (src != null && src instanceof JTextComponent) {
//          JTextComponent textField = (JTextComponent)src;
//
//          textField.setText(textField.getText());
//          textField.selectAll();
//        }
//      }
//    });
  }

  /**
   * Constructs a <code>DefaultCellEditor</code> object that uses a check box.
   *
   * @param checkBox a <code>JCheckBox</code> object
   */
  public CellEditor(final JCheckBox checkBox) {
    editorComponent = checkBox;
    delegate = new EditorDelegate() {
      public void setValue(Object value) {
        boolean selected = false;
        if (value instanceof Boolean) {
          selected = ((Boolean)value).booleanValue();
        }
        else if (value instanceof String) {
          selected = value.equals("true");
        }
        checkBox.setSelected(selected);
      }

      public Object getCellEditorValue() {
        return Boolean.valueOf(checkBox.isSelected());
      }
    };
    checkBox.addActionListener(delegate);
    checkBox.setRequestFocusEnabled(false);
  }

  /**
   * Constructs a <code>DefaultCellEditor</code> object that uses a combo box.
   *
   * @param comboBox a <code>JComboBox</code> object
   */
  public CellEditor(final JComboBox comboBox) {
    editorComponent = comboBox;
    comboBox.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);
    comboBox.setBorder(BorderFactory.createLineBorder(Colors.getColor("black")));
//    comboBox.setBorder(BorderFactory.createEmptyBorder());
    delegate = new EditorDelegate() {
      public void setValue(Object value) {
        if (value instanceof Category) {
          Category c = (Category)value;
          if (comboBox.getSelectedItem() != null && !((Category)comboBox.getSelectedItem()).getId().equals(c.getId())) {
            for (int i = 0; i < comboBox.getItemCount(); i++) {
              if (comboBox.getItemAt(i) != null && ((Category)comboBox.getItemAt(i)).getId().equals(c.getId()))
                comboBox.setSelectedIndex(i);
            }
          }
        }
      }

      @Override public void itemStateChanged(ItemEvent e) {
        super.itemStateChanged(e);    //To change body of overridden methods use File | Settings | File Templates.
      }

      @Override public boolean isCellEditable(EventObject anEvent) {
        return super.isCellEditable(anEvent);    //To change body of overridden methods use File | Settings | File Templates.
      }

      @Override public boolean startCellEditing(EventObject anEvent) {
        return super.startCellEditing(anEvent);    //To change body of overridden methods use File | Settings | File Templates.
      }

      @Override public void cancelCellEditing() {
        super.cancelCellEditing();    //To change body of overridden methods use File | Settings | File Templates.
      }

      @Override public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);    //To change body of overridden methods use File | Settings | File Templates.
      }

      public Object getCellEditorValue() {
        return comboBox.getSelectedItem();
      }

      public boolean shouldSelectCell(EventObject anEvent) {
        if (anEvent instanceof MouseEvent) {
          MouseEvent e = (MouseEvent)anEvent;
          return e.getID() != MouseEvent.MOUSE_DRAGGED;
        }
        return true;
      }

      public boolean stopCellEditing() {
        if (comboBox.isEditable()) {
          // Commit edited value.
          comboBox.actionPerformed(new ActionEvent(CellEditor.this, 0, ""));
        }
        return super.stopCellEditing();
      }
    };
    comboBox.addActionListener(delegate);
  }

  /**
   * Returns a reference to the editor component.
   *
   * @return the editor <code>Component</code>
   */
  public Component getComponent() {
    return editorComponent;
  }

//
//  Modifying
//

  /**
   * Specifies the number of clicks needed to start editing.
   *
   * @param count an int specifying the number of clicks needed to start editing
   *
   * @see #getClickCountToStart
   */
  public void setClickCountToStart(int count) {
    clickCountToStart = count;
  }

  /**
   * Returns the number of clicks needed to start editing.
   *
   * @return the number of clicks needed to start editing
   */
  public int getClickCountToStart() {
    return clickCountToStart;
  }

//
//  Override the implementations of the superclass, forwarding all methods
//  from the CellEditor interface to our delegate.
//

  /**
   * Forwards the message from the <code>CellEditor</code> to the <code>delegate</code>.
   *
   * @see EditorDelegate#getCellEditorValue
   */
  public Object getCellEditorValue() {
    return delegate.getCellEditorValue();
  }

  /**
   * Forwards the message from the <code>CellEditor</code> to the <code>delegate</code>.
   *
   * @see EditorDelegate#isCellEditable(EventObject)
   */
  public boolean isCellEditable(EventObject anEvent) {
    return delegate.isCellEditable(anEvent);
  }

  /**
   * Forwards the message from the <code>CellEditor</code> to the <code>delegate</code>.
   *
   * @see EditorDelegate#shouldSelectCell(EventObject)
   */
  public boolean shouldSelectCell(EventObject anEvent) {
    return delegate.shouldSelectCell(anEvent);
  }

  /**
   * Forwards the message from the <code>CellEditor</code> to the <code>delegate</code>.
   *
   * @see EditorDelegate#stopCellEditing
   */
  public boolean stopCellEditing() {
    return delegate.stopCellEditing();
  }

  /**
   * Forwards the message from the <code>CellEditor</code> to the <code>delegate</code>.
   *
   * @see EditorDelegate#cancelCellEditing
   */
  public void cancelCellEditing() {
    delegate.cancelCellEditing();
  }

//
//  Implementing the TreeCellEditor Interface
//

  /** Implements the <code>TreeCellEditor</code> interface. */
  public Component getTreeCellEditorComponent(JTree tree, Object value,
      boolean isSelected,
      boolean expanded,
      boolean leaf, int row) {
    String stringValue = tree.convertValueToText(value, isSelected,
        expanded, leaf, row, false);

    delegate.setValue(stringValue);
    return editorComponent;
  }

//

  //  Implementing the CellEditor Interface
//
  /** Implements the <code>TableCellEditor</code> interface. */
  public Component getTableCellEditorComponent(JTable table, Object value,
      boolean isSelected,
      int row, int column) {
    delegate.setValue(value);
    if (editorComponent instanceof JCheckBox) {
      //in order to avoid a "flashing" effect when clicking a checkbox
      //in a table, it is important for the editor to have as a border
      //the same border that the renderer has, and have as the background
      //the same color as the renderer has. This is primarily only
      //needed for JCheckBox since this editor doesn't fill all the
      //visual space of the table cell, unlike a text field.
      TableCellRenderer renderer = table.getCellRenderer(row, column);
      Component c = renderer.getTableCellRendererComponent(table, value,
          isSelected, true, row, column);
      if (c != null) {
        editorComponent.setOpaque(true);
        editorComponent.setBackground(c.getBackground());
        if (c instanceof JComponent) {
          editorComponent.setBorder(((JComponent)c).getBorder());
        }
      }
      else {
        editorComponent.setOpaque(false);
      }
    }
    return editorComponent;
  }


//
//  Protected EditorDelegate class
//

  /** The protected <code>EditorDelegate</code> class. */
  protected class EditorDelegate implements ActionListener, ItemListener, Serializable {

    /** The value of this cell. */
    protected Object value;

    /**
     * Returns the value of this cell.
     *
     * @return the value of this cell
     */
    public Object getCellEditorValue() {
      return value;
    }

    /**
     * Sets the value of this cell.
     *
     * @param value the new value of this cell
     */
    public void setValue(Object value) {
      this.value = value;
    }

    /**
     * Returns true if <code>anEvent</code> is <b>not</b> a <code>MouseEvent</code>.  Otherwise, it returns true if the
     * necessary number of clicks have occurred, and returns false otherwise.
     *
     * @param anEvent the event
     *
     * @return true  if cell is ready for editing, false otherwise
     *
     * @see #setClickCountToStart
     * @see #shouldSelectCell
     */
    public boolean isCellEditable(EventObject anEvent) {
      if (anEvent instanceof MouseEvent) {
        return ((MouseEvent)anEvent).getClickCount() >= clickCountToStart;
      }
      return true;
    }

    /**
     * Returns true to indicate that the editing cell may be selected.
     *
     * @param anEvent the event
     *
     * @return true
     *
     * @see #isCellEditable
     */
    public boolean shouldSelectCell(EventObject anEvent) {
      return true;
    }

    /**
     * Returns true to indicate that editing has begun.
     *
     * @param anEvent the event
     */
    public boolean startCellEditing(EventObject anEvent) {
      return true;
    }

    /**
     * Stops editing and returns true to indicate that editing has stopped. This method calls <code>fireEditingStopped</code>.
     *
     * @return true
     */
    public boolean stopCellEditing() {
      fireEditingStopped();
      return true;
    }

    /** Cancels editing.  This method calls <code>fireEditingCanceled</code>. */
    public void cancelCellEditing() {
      fireEditingCanceled();
    }

    /**
     * When an action is performed, editing is ended.
     *
     * @param e the action event
     *
     * @see #stopCellEditing
     */
    public void actionPerformed(ActionEvent e) {
      CellEditor.this.stopCellEditing();
    }

    /**
     * When an item's state changes, editing is ended.
     *
     * @param e the action event
     *
     * @see #stopCellEditing
     */
    public void itemStateChanged(ItemEvent e) {
      CellEditor.this.stopCellEditing();
    }
  }

} // End of class JCellEditor
