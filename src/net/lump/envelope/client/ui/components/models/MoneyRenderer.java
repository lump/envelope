package us.lump.envelope.client.ui.components.models;

import us.lump.envelope.client.ui.defs.Colors;
import us.lump.envelope.client.ui.defs.Fonts;
import us.lump.lib.Money;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class MoneyRenderer extends DefaultTableCellRenderer {
  public MoneyRenderer() {
    super();
  }


  public Component getTableCellRendererComponent(JTable table,
      Object value,
      boolean isSelected,
      boolean hasFocus,
      int row,
      int col) {
    JLabel label = new JLabel(
        value == null ? "" : ((Money)value).toFormattedString(),
        SwingConstants.RIGHT);
    label.setFont(Fonts.fixed.getFont());
    label.setBorder(
        new CompoundBorder(
            new EmptyBorder(new Insets(1, 4, 1, 4)),
            label.getBorder()));

    if (value != null && ((Money)value).doubleValue() < 0)
      label.setForeground(Colors.getColor("red"));
//      else
//        label.setForeground(Colors.getColor("green"));

    Color original = label.getBackground();

    // this is for Nimbus's alternate row color, shouldn't affect other stuff
    if (UIManager.getLookAndFeel().getID().equals("Nimbus") &&
        (original == null || original instanceof javax.swing.plaf.UIResource)) {
      Color alternateColor = (Color)UIManager.get("Table.alternateRowColor");
      if (alternateColor != null && row % 2 == 0) {
        label.setBackground(alternateColor);
        label.setOpaque(true);
      }
      else {
        label.setBackground(original);
        label.setOpaque(false);
      }

    }

    if (isSelected) {
      label.setBackground(table.getSelectionBackground());
      label.setForeground(table.getSelectionForeground());
      label.setOpaque(true);
    }
    if (hasFocus) {
      if (table.isCellEditable(row, col)) {
        table.editCellAt(row, col);
      }
      else {
        label.setForeground(table.getSelectionForeground());
        label.setBackground(table.getSelectionBackground());
        label.setOpaque(true);
      }
    }

    return label;
  }
}