package us.lump.envelope.client.ui.components.models;

import us.lump.envelope.entity.Allocation;
import us.lump.lib.Money;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * This provides the model for the allocation list in the form.
 *
 * @author Troy Bowman
 * @version $Id: AllocationFormTableModel.java,v 1.3 2009/05/31 21:45:30 troy Exp $
 */
public class AllocationFormTableModel extends AbstractTableModel {

  List<Allocation> allocations;

  public static enum SIMPLE_COLUMN {
    Category,
    Allocation,
  }

  public static enum PRESET_COLUMN {
    Category,
    Allocation,
    Projected,
    Settings,
  }


  public AllocationFormTableModel() {
    allocations = new ArrayList<Allocation>();
  }

  public void setAllocations(List<Allocation> allocations) {
    int oldSize = this.allocations.size();
    this.allocations = allocations;

    fireTableRowsUpdated(0, oldSize - 1);
    if (oldSize > allocations.size())
      fireTableRowsDeleted(allocations.size(), oldSize - 1);
    if (oldSize < allocations.size())
      fireTableRowsInserted(oldSize, allocations.size() - 1);

  }

  public List<Allocation> getAllocations() {
    return allocations;
  }

  public String getColumnName(int columnIndex) {
    return SIMPLE_COLUMN.values()[columnIndex].toString();
  }

  public Class<?> getColumnClass(int columnIndex) {
    if (allocations.size() == 0) return null;
    return columnIndex == 0 ? String.class : Money.class;
  }

  public int getRowCount() {
    return allocations.size();
  }

  public int getColumnCount() {
    return 2;
  }

  public Object getValueAt(int row, int column) {
    Allocation a = allocations.get(row);
    return column == 0 ? a.getCategory() : a.getAmount();
  }
}
