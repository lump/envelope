package net.lump.envelope.client.ui.components.forms.transaction;

import net.lump.envelope.shared.entity.Allocation;
import net.lump.envelope.shared.entity.Category;
import net.lump.lib.Money;

import javax.swing.*;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This provides the model for the allocation list in the form.
 *
 * @author Troy Bowman
 */
public class AllocationFormTableModel extends AbstractTableModel {

  JTable table;
  List<Allocation> allocations;

  /**
   * Category id to that category's balance as of this transaction's day,
   * WITHOUT this transaction, as the server last reported it.  Null until read,
   * or if the read failed, and the two computed columns show nothing rather
   * than a number built on zero.
   *
   * <p>As of the day, because an old transaction met the balance of its own
   * time, and today's less itself would be a fiction.  Leaving the transaction
   * out is what makes the number safe to refresh at any time.  The form saves
   * as it is edited, so a plain sum would come to include this transaction's
   * rows as each save landed, and whether a read taken mid-edit counted a row
   * or not would depend on which round trip won.  With the transaction
   * excluded on the server, our own saves never move it; only other
   * transactions do, which is exactly what a fresh read is for.
   */
  private Map<Integer, Money> balances;

  /**
   * Whether {@link #balances} covers every category, as the upfront read does
   * (a category it lacks then has nothing to sum and is zero), or only the ones
   * read one at a time since (an absent category is simply not known yet).
   */
  private boolean balancesComplete;

  boolean expense = false;

  enum Columns {
    Category(Category.class, true),
    Balance(Money.class, false),
    Allocation(Money.class, true),
    Projection(Money.class, false);

    final Class columnClass;
    final Boolean editable;

    Columns(Class c, Boolean e) {
      columnClass = c;
      editable = e;
    }
  }

  /**
   * Notified when a cell edit has actually changed an Allocation.  This model owns
   * the in-memory mutation; persisting it is the listener's business, because an
   * Allocation has to be saved on its own -- Transaction.allocations is a
   * no-cascade inverse side, so saving the Transaction does not carry it.
   */
  public interface EditListener {
    void allocationEdited(Allocation allocation);

    /**
     * The user has put a category on a row.  Rows entered by hand arrive
     * seconds apart, so this is the moment to read that one category's balance
     * afresh (see {@link #setBalance}) rather than trust the upfront read.
     */
    void categoryChosen(Allocation allocation);
  }

  private EditListener editListener;

  public void setEditListener(EditListener editListener) {
    this.editListener = editListener;
  }

  public AllocationFormTableModel(JTable table) {
    this(table, false);
  }

  public AllocationFormTableModel(JTable table, boolean expense) {
    this.table = table;
    allocations = new ArrayList<Allocation>();
    setExpense(expense);
  }

  public void setExpense(boolean expense) {
    if (this.expense != expense) {
      // Changing the view while a cell is being edited would re-sign whatever
      // that editor later hands back under the new convention.  The handler
      // commits open editors before calling this; refuse to let the view move
      // under one anyway, since a second caller would reintroduce the flip.
      if (table != null && table.isEditing() && table.getCellEditor() != null)
        table.getCellEditor().stopCellEditing();

      this.expense = expense;
      if (allocations != null)
        fireTableRowsUpdated(0, allocations.size() - 1);
    }
  }

  /**
   * Show a transaction's allocations, with every category's balance without
   * that transaction: one grouped read, which is the right shape for a
   * transaction arriving with dozens of rows, from the list or from a preset.
   *
   * @param allocations the rows, which is the transaction's own list
   * @param balances    category id to balance for the whole budget, or null
   *                    if it could not be read
   */
  public void setAllocations(List<Allocation> allocations, Map<Integer, Money> balances) {
    int oldSize = this.allocations == null ? 0 : this.allocations.size();
    this.allocations = allocations;
    setBalances(balances);

    if (allocations != null) {
      fireTableRowsUpdated(0, oldSize - 1);
      if (oldSize > allocations.size())
        fireTableRowsDeleted(allocations.size(), oldSize - 1);
      if (oldSize < allocations.size())
        fireTableRowsInserted(oldSize, allocations.size() - 1);
    }
  }

  /**
   * Replace every balance: the transaction's day moved, so they are all as of a
   * different day now.
   *
   * @param balances category id to balance for the whole budget, or null if
   *                 it could not be read
   */
  public void setBalances(Map<Integer, Money> balances) {
    this.balances = balances == null ? null : new HashMap<Integer, Money>(balances);
    this.balancesComplete = balances != null;
    fireAllRowsUpdated();
  }

  /**
   * A fresh balance for one category, read on its own.  Every row's two computed
   * cells are repainted, since a category can be on several rows.
   *
   * @param categoryId the category
   * @param balance    its balance without this transaction
   */
  public void setBalance(Integer categoryId, Money balance) {
    if (categoryId == null || balance == null) return;
    if (balances == null) {
      balances = new HashMap<Integer, Money>();
      balancesComplete = false;
    }
    balances.put(categoryId, balance);
    fireAllRowsUpdated();
  }

  /**
   * What the category holds before this transaction.
   *
   * @return the balance, or null if it is not known or the row has no category
   */
  private Money balanceBefore(Allocation allocation) {
    Category category = allocation.getCategory();
    if (balances == null || category == null) return null;
    Money balance = balances.get(category.getId());
    if (balance == null && balancesComplete) balance = Money.ZERO;
    return balance;
  }

  /**
   * What the category will hold after this transaction: the balance before it
   * plus every row of this transaction in that category, as they stand on
   * screen.  Summing the rows rather than adding just this one is what makes
   * an auto-deduct pair -- the amount in and the same amount straight back out
   * -- project to the balance it leaves, rather than one row up and the other
   * down.  The stored amounts are used, not the view's: an expense is negative
   * and comes off whichever radio is selected.
   *
   * @return the projection, or null if the balance before is not known
   */
  private Money projection(Allocation allocation) {
    Money sum = balanceBefore(allocation);
    if (sum == null) return null;
    Integer categoryId = allocation.getCategory().getId();
    for (Allocation a : allocations)
      if (a.getCategory() != null && categoryId.equals(a.getCategory().getId()) && a.getAmount() != null)
        sum = sum.add(a.getAmount());
    return sum;
  }

  /**
   * Repaint every row: the projections of a category's other rows move with
   * any one of them, and a category change touches two categories' worth.
   */
  private void fireAllRowsUpdated() {
    if (allocations != null && !allocations.isEmpty())
      fireTableRowsUpdated(0, allocations.size() - 1);
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
    return Columns.values().length;
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
    Allocation allocation = allocations.get(row);

    // Swing calls setValueAt whenever an editor stops, whether or not anything
    // changed, so each branch returns early on a no-op rather than firing a save.
    boolean categoryChosen = false;
    switch (Columns.values()[column]) {
      case Category:
        if (value.equals(allocation.getCategory())) return;
        allocation.setCategory((Category)value);
        categoryChosen = true;
        break;
      case Allocation:
        try {
          Money m = value instanceof Money ? (Money)value : new Money(value.toString().trim());
          Money amount = expense ? m.negate() : m;
          if (amount.equals(allocation.getAmount())) return;
          allocation.setAmount(amount);
        } catch (NumberFormatException nfe) {
          return;
        }
        break;
      default:
        return;   // the computed columns are not written to
    }
    fireAllRowsUpdated();
    if (editListener != null) {
      editListener.allocationEdited(allocation);
      if (categoryChosen) editListener.categoryChosen(allocation);
    }
  }

  public Object getValueAt(int row, int column) {
    if (allocations == null) return null;
    Allocation allocation = allocations.get(row);
    Object retval = null;

    switch (Columns.values()[column]) {
      case Category:
        retval = allocation.getCategory();
        break;
      case Balance:
        retval = balanceBefore(allocation);
        break;
      case Allocation:
        retval = expense ? allocation.getAmount().negate() : allocation.getAmount();
        break;
      case Projection:
        retval = projection(allocation);
        break;
    }

    return retval;
  }

  public List<Allocation> getAllocations() {
    return allocations;
  }

  public boolean hasEmptyRow() {
    if (allocations.size() == 0) return false;

    int last = getRowCount() - 1;
    return getValueAt(last, Columns.Category.ordinal()) == null
        || Money.ZERO.equals(getValueAt(last, Columns.Allocation.ordinal()));
  }

  public void addEmptyRow(Allocation a) {
    allocations.add(a);
    fireTableRowsInserted(allocations.size()-1, allocations.size()-1);
  }

  /**
   * Drop one row.  The caller is responsible for having removed it from the
   * database first -- this only updates what is on screen.
   *
   * <p>Takes the Allocation rather than a row index because the delete round trip
   * is asynchronous and rows may have shifted by the time it returns, and matches
   * on identity because Allocation.equals is not dependable for this (it compares
   * the parent Transaction, and two rows can compare equal).
   *
   * @param allocation the row to remove
   */
  public void removeRow(Allocation allocation) {
    if (allocations == null) return;
    for (int i = 0; i < allocations.size(); i++)
      if (allocations.get(i) == allocation) {
        allocations.remove(i);
        fireTableRowsDeleted(i, i);
        fireAllRowsUpdated();   // its category's other rows project differently now
        return;
      }
  }

  /**
   * Tell the table that one row's Allocation was changed from outside it -- by the
   * transaction amount field writing through, say.  Matches on identity, as
   * removeRow does and for the same reason.
   *
   * @param allocation the row whose contents changed
   */
  public void rowChanged(Allocation allocation) {
    if (allocations == null) return;
    for (int i = 0; i < allocations.size(); i++)
      if (allocations.get(i) == allocation) {
        fireAllRowsUpdated();
        return;
      }
  }

  /**
   * The Category to start a newly added row on.  Both category and amount are NOT
   * NULL in the database, so a new row has to be born valid; the last row's
   * category keeps the new one in the same account, which is nearly always what
   * is wanted when splitting a transaction.
   *
   * @return a Category, or null if there is nothing to copy from
   */
  public Category defaultCategory() {
    if (allocations == null) return null;
    for (int i = allocations.size() - 1; i >= 0; i--)
      if (allocations.get(i).getCategory() != null)
        return allocations.get(i).getCategory();
    return null;
  }

}
