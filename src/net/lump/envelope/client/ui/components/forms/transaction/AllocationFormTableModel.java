package net.lump.envelope.client.ui.components.forms.transaction;

import net.lump.envelope.client.CriteriaFactory;
import net.lump.envelope.client.portal.HibernatePortal;
import net.lump.envelope.shared.entity.Allocation;
import net.lump.envelope.shared.entity.Category;
import net.lump.envelope.shared.exception.AbortException;
import net.lump.lib.Money;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import javax.swing.*;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * This provides the model for the allocation list in the form.
 *
 * @author Troy Bowman
 * @version $Id: AllocationFormTableModel.java,v 1.8 2010/02/15 05:51:52 troy Exp $
 */
public class AllocationFormTableModel extends AbstractTableModel {

  JTable table;
  List<Allocation> allocations;

  private static final String BALANCE_CACHE = "allocationFormBalanceCache";
  private static final Cache balanceCache = new Cache(BALANCE_CACHE, 256, false, false, 30, 60);

  Mode mode;
  boolean expense = false;

  static {
    CacheManager.getInstance().addCache(balanceCache);
  }

  enum Mode {
    Simple,
    Complex
  }

  enum Columns {
    Category(Category.class, true),
    Allocation(Money.class, true),
//   Projected(Money.class, false);
    ;

    final Class columnClass;
    final Boolean editable;

    Columns(Class c, Boolean e) {
      columnClass = c;
      editable = e;
    }
  }

  public AllocationFormTableModel(JTable table) {
    this(table, Mode.Simple, false);
  }

  public AllocationFormTableModel(JTable table, Mode mode, boolean expense) {
    this.table = table;
    allocations = new ArrayList<Allocation>();
    setExpense(expense);
    setMode(mode);
  }

  public void setExpense(boolean expense) {
    if (this.expense != expense) {
      this.expense = expense;
      if (allocations != null)
        fireTableRowsUpdated(0, allocations.size() - 1);
    }
  }

  public void setMode(Mode mode) {
    this.mode = mode;

  }

  private void populateCategoryBalances() {
    Object categoryBalances = null;
    try {
      for (Object o : new HibernatePortal().detachedCriteriaQueryList(CriteriaFactory.getInstance().getAllBalances())) {
        balanceCache.put(
            new Element(
                ((Category)((Object[])o)[0]).getId(),
                (Money)(((Object[])o)[1] == null ? new Money(0) : ((Object[])o)[1])
            )
        );
      }
    } catch (AbortException ignore) {}
  }

  @SuppressWarnings({"unchecked"}) private Money getCategoryBalance(Allocation allocation) {
    Money output = new Money(0);

    Element e = balanceCache.get(allocation.getCategory().getId());
    if (e != null)
      output = (Money)e.getValue();
    else {
      populateCategoryBalances();
      e = balanceCache.get(allocation.getCategory().getId());
      if (e != null)
        output = (Money)e.getValue();
    }
    return output;
  }

  public void setAllocations(List<Allocation> allocations) {
    int oldSize = this.allocations == null ? 0 : this.allocations.size();
    this.allocations = allocations;

    if (allocations != null) {
      fireTableRowsUpdated(0, oldSize - 1);
      if (oldSize > allocations.size())
        fireTableRowsDeleted(allocations.size(), oldSize - 1);
      if (oldSize < allocations.size())
        fireTableRowsInserted(oldSize, allocations.size() - 1);
    }
  }

  @Override
  public String getColumnName(int columnIndex) {
    return Columns.values()[columnIndex].toString();
  }

  @Override
  public Class<?> getColumnClass(int columnIndex) {
    if (allocations == null || allocations.size() == 0) return null;
    return Columns.values()[columnIndex].columnClass;
  }

  public int getRowCount() {
    return allocations == null ? 0 : allocations.size();
  }

  public int getColumnCount() {
    switch (mode) {
      case Simple:
        return 2;
      case Complex:
        return 3;
      default:
        return 0;
    }
  }

  @Override
  public void removeTableModelListener(TableModelListener l) {
    super.removeTableModelListener(l);
  }

  @Override
  public boolean isCellEditable(int row, int column) {
    return Columns.values()[column].editable;
  }

  @Override
  public void setValueAt(Object value, int row, int column) {
    if (value == null) return;
    switch (Columns.values()[column]) {
      case Category:
        allocations.get(row).setCategory((Category)value);
        break;
      case Allocation:
        try {
          Money m = value instanceof Money ? (Money)value : new Money(value.toString().trim());
          allocations.get(row).setAmount(expense ? m.negate() : m);
        } catch (NumberFormatException nfe) {
          return;
        }
        break;
    }
    fireTableRowsUpdated(row, row);
  }

  public Object getValueAt(int row, int column) {
    if (allocations == null) return null;
    Allocation allocation = allocations.get(row);
    Object retval = null;

    switch (Columns.values()[column]) {
      case Category:
        retval = allocation.getCategory();
        break;
      case Allocation:
        Money amount = allocation.getAmount();
        retval = expense ? allocation.getAmount().negate() : allocation.getAmount();
        break;
//      case Projected:
//        Money balance = getBalance(editAllocation);
//        Money amount = editAllocation.getNetAmount();
//        if (originalAllocation != null)
//          amount = amount.subtract(originalAllocation.getNetAmount());
//        retval = balance.add(amount);
//        break;
    }

    return retval;
  }

  public List<Allocation> getAllocations() {
    return allocations;
  }

  public boolean hasEmptyRow() {
    if (allocations.size() == 0) return false;

    Allocation allocation = allocations.get(allocations.size()-1);
    if (allocation.getCategory() == null && allocation.getAmount() == null)
      return true;
    else return false;
  }

  public void addEmptyRow() {
    allocations.add(new Allocation());
    fireTableRowsInserted(allocations.size()-1, allocations.size()-1);
  }
}
