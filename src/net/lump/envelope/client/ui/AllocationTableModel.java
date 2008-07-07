package us.lump.envelope.client.ui;

import us.lump.envelope.entity.Category;
import us.lump.envelope.entity.Transaction;
import us.lump.envelope.entity.Allocation;
import us.lump.envelope.client.CriteriaFactory;

import javax.swing.table.AbstractTableModel;
import java.util.List;
import java.util.Date;

/**
 * A table model which lists transactions.
 *
 * @author Troy Bowman
 * @version $Id: AllocationTableModel.java,v 1.2 2008/07/07 06:04:34 troy Exp $
 */
public class AllocationTableModel extends AbstractTableModel {
  private List<Allocation> allocations;


  AllocationTableModel(Category account) {
    Date date = new Date((System.currentTimeMillis() - (86400000L*30L)));
    allocations =
        CriteriaFactory.getInstance().getAllocationsForCategory(account, date);
  }

  public int getRowCount() {
    return allocations.size();
  }

  public int getColumnCount() {
    return 5;
  }

  public Object getValueAt(int rowIndex, int columnIndex) {
    Allocation a = allocations.get(rowIndex);

    switch (columnIndex) {
      case 0:
        return a.getTransaction().getDate();
      case 1:
        return a.getAmount();
      case 2:
//        return a.getTags();
        return null;
      case 3:
        return a.getTransaction().getEntity();
      case 4:
        return a.getTransaction().getDescription();
    }
    return null;
  }

  public String getColumnName(int columnIndex) {
    switch (columnIndex) {
      case 0:
        return "Date";
      case 1:
        return "Amount";
      case 2:
        return "Tags";
      case 3:
        return "Entity";
      case 4:
        return "Description";
    }
    return null;

  }
}
