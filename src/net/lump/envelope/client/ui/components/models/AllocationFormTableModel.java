package us.lump.envelope.client.ui.components.models;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import us.lump.envelope.client.CriteriaFactory;
import us.lump.envelope.client.portal.HibernatePortal;
import us.lump.envelope.shared.entity.Allocation;
import us.lump.envelope.shared.entity.Category;
import us.lump.envelope.shared.entity.Transaction;
import us.lump.envelope.shared.exception.AbortException;
import us.lump.lib.Money;

import javax.swing.*;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This provides the model for the allocation list in the form.
 *
 * @author Troy Bowman
 * @version $Id: AllocationFormTableModel.java,v 1.6 2009/08/28 22:07:46 troy Exp $
 */
public class AllocationFormTableModel extends AbstractTableModel {

  Transaction editingTransaction;
  Transaction originalTransaction;
  JTable table;

  private static final String BALANCE_CACHE = "allocationFormBalanceCache";

  private static final Cache balanceCache = new Cache(BALANCE_CACHE, 128, false, false, 30, 60);

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
    Projected(Money.class, false);

    final Class columnClass;
    final Boolean editable;

    Columns(Class c, Boolean e) {
      columnClass = c;
      editable = e;
    }
  }



  public AllocationFormTableModel(JTable table) {
    this(table, Mode.Complex, false);
  }

  public AllocationFormTableModel(JTable table, Mode mode, boolean expense) {
    this.table = table;
    originalTransaction = new Transaction();
    editingTransaction = new Transaction();
    setExpense(expense);
    setMode(mode);
  }

  public void setExpense(boolean expense) {
    this.expense = expense;
  }

  public void setMode(Mode mode) {
    this.mode = mode;

  }

  @SuppressWarnings({"unchecked"}) private Money getBalance(Allocation allocation) {
    Money retval = null;

    if (editingTransaction != null
        && editingTransaction.getAllocations() != null
        && editingTransaction.getAllocations().size() > 0) {
      Element e = balanceCache.get(editingTransaction.getId());
      HashMap<Integer,Money> map = (e == null ? null : (HashMap<Integer,Money>)e.getValue());
      if (allocation.getCategory() != null
          && map != null
          && map.size() > 0
          && map.containsKey(allocation.getCategory().getId())
          && map.get(allocation.getCategory().getId()) != null) {
        retval = map.get(allocation.getCategory().getId());
      }
      else {
        try {
          List<Category> categoryList = new ArrayList<Category>();

          for (Allocation a : editingTransaction.getAllocations()) 
            if (a.getCategory() != null) categoryList.add(a.getCategory());

          Object q = new HibernatePortal().detachedCriteriaQueryList(
              CriteriaFactory.getInstance().getBalances(categoryList));
          if (q != null && q instanceof ArrayList) {
            HashMap<Integer, Money> resultMap = new HashMap<Integer, Money>();
            for (Object o : (ArrayList)q) {
              resultMap.put(((Category)((Object[])o)[0]).getId(),
                  (Money)(((Object[])o)[1] == null ? new Money(0) : ((Object[])o)[1]));
            }
            balanceCache.put(new Element(editingTransaction.getId(), resultMap));
            retval = resultMap.get(allocation.getCategory() != null ? allocation.getCategory().getId() : null);
          }
        } catch (AbortException ignore) {}
      }
    }

    return retval == null ? new Money(0) : retval;
  }

  public void setTransaction(Transaction editingTransaction, Transaction originalTransaction) {
    int oldSize = this.editingTransaction.getAllocations() == null ? 0 : this.editingTransaction.getAllocations().size();
    this.editingTransaction = editingTransaction;
    this.originalTransaction = originalTransaction;

    fireTableRowsUpdated(0, oldSize - 1);
    if (oldSize > editingTransaction.getAllocations().size())
      fireTableRowsDeleted(editingTransaction.getAllocations().size(), oldSize - 1);
    if (oldSize < editingTransaction.getAllocations().size())
      fireTableRowsInserted(oldSize, editingTransaction.getAllocations().size() - 1);
  }

  public Transaction getTransaction() {
    return editingTransaction;
  }

  @Override
  public String getColumnName(int columnIndex) {
    return Columns.values()[columnIndex].toString();
  }

  @Override
  public Class<?> getColumnClass(int columnIndex) {
    if (editingTransaction.getAllocations() == null || editingTransaction.getAllocations().size() == 0) return null;
    return Columns.values()[columnIndex].columnClass;
  }

  public int getRowCount() {
    if (editingTransaction.getAllocations() == null) return 0;
    return editingTransaction.getAllocations().size();
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
        editingTransaction.getAllocations().get(row).setCategory((Category)value);
        break;
      case Allocation:
        try {
          Money m = value instanceof Money ? (Money)value : new Money(value.toString().trim());
          editingTransaction.getAllocations().get(row).setAmount(expense ? m.negate() : m);
        } catch (NumberFormatException nfe) {
          return;
        }
        break;
    }
    fireTableRowsUpdated(row, row);
  }

  public Object getValueAt(int row, int column) {
    if (editingTransaction.getAllocations() == null) return null;
    Allocation editAllocation = editingTransaction.getAllocations().get(row);
    Allocation originalAllocation = originalTransaction.getAllocations().get(row);
    Object retval = null;


    switch (mode) {
      case Simple:
        retval = column == 0 ? editAllocation.getCategory() : editAllocation.getAmount();
        break;
      case Complex:
        switch (Columns.values()[column]) {
          case Category:
            retval = editAllocation.getCategory();
            break;
          case Allocation:
            retval = expense ? editAllocation.getAmount().negate() : editAllocation.getAmount();
            break;
          case Projected:
            Money balance = getBalance(editAllocation);
            Money amount = editAllocation.getAmount();
            if (originalAllocation != null)
              amount = amount.subtract(originalAllocation.getAmount());
            retval = balance.add(amount);
            break;
        }
    }

    return retval;
  }
}
