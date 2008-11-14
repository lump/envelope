package us.lump.envelope.client.ui.components.models;

import us.lump.envelope.entity.Allocation;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * This provides the model for the allocation list in the form.
 *
 * @author Troy Bowman
 * @version $Id: AllocationFormTableModel.java,v 1.2 2008/11/14 07:48:49 troy Test $
 */
public class AllocationFormTableModel extends AbstractTableModel {

  List<Allocation> allocations;

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
