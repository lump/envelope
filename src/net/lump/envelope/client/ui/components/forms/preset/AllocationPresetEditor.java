package net.lump.envelope.client.ui.components.forms.preset;

import net.lump.envelope.client.CriteriaFactory;
import net.lump.envelope.client.State;
import net.lump.envelope.client.portal.BudgetPortal;
import net.lump.envelope.client.portal.HibernatePortal;
import net.lump.envelope.client.thread.StatusRunnable;
import net.lump.envelope.client.thread.ThreadPool;
import net.lump.envelope.client.ui.defs.Strings;
import net.lump.envelope.shared.entity.AllocationPreset;
import net.lump.envelope.shared.entity.Budget;
import net.lump.envelope.shared.entity.Category;
import net.lump.envelope.shared.exception.AbortException;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Editor for allocation presets.
 *
 * <p>A preset is not a row of its own anywhere: it is however many
 * allocation_presets rows happen to share a name. So "the presets" is the set of
 * distinct names, "a preset" is the rows carrying one, and a preset with no rows
 * left has simply stopped existing -- which is why creating one starts it with a
 * row, and why deleting the last row deletes the preset with it.
 *
 * <p>Each edit is written as it is made, the way the transaction form works. There
 * is no OK/Cancel because there is nothing held back to cancel.
 *
 * @author troy
 */
public class AllocationPresetEditor extends JDialog {

  private final Budget budget;
  private final List<Category> categories = new ArrayList<Category>();

  private final JComboBox<String> presetPicker = new JComboBox<String>();
  private final RowTableModel model = new RowTableModel();
  private final JTable table = new JTable(model);
  private final JLabel status = new JLabel(" ");

  /** true while the picker is being repopulated, so its listener stays quiet */
  private boolean loading = false;

  /**
   * Rows with a save in flight, each mapped to whether it was edited again while
   * that save was running.  The pool starts every task at once, so without this
   * three quick edits to one row race: the first bumps the @Version stamp and the
   * others are refused as stale.  Keyed by identity, as the transaction form does.
   */
  private final Map<AllocationPreset, Boolean> savesInFlight =
      new IdentityHashMap<AllocationPreset, Boolean>();

  public AllocationPresetEditor(Window owner, Budget budget) {
    super(owner, Strings.get("allocation.presets"), ModalityType.APPLICATION_MODAL);
    this.budget = budget;

    setLayout(new BorderLayout(6, 6));
    ((JComponent)getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    add(buildPresetBar(), BorderLayout.NORTH);
    add(new JScrollPane(table), BorderLayout.CENTER);
    add(buildRowBar(), BorderLayout.SOUTH);

    table.setRowHeight(table.getRowHeight() + 4);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.putClientProperty("terminateEditOnFocusLost", true);

    setPreferredSize(new Dimension(620, 420));
    pack();
    setLocationRelativeTo(owner);

    reload(null);
  }

  private JPanel buildPresetBar() {
    JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    bar.add(new JLabel(Strings.get("presets")));
    bar.add(presetPicker);

    presetPicker.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (!loading) showRowsFor((String)presetPicker.getSelectedItem());
      }
    });

    bar.add(button("new.preset", new Runnable() {
      public void run() { newPreset(); }
    }));
    bar.add(button("rename.preset", new Runnable() {
      public void run() { renamePreset(); }
    }));
    bar.add(button("delete.preset", new Runnable() {
      public void run() { deletePreset(); }
    }));
    return bar;
  }

  private JPanel buildRowBar() {
    JPanel bar = new JPanel(new BorderLayout(6, 0));

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    buttons.add(button("add.row", new Runnable() {
      public void run() { addRow(); }
    }));
    buttons.add(button("delete.row", new Runnable() {
      public void run() { deleteRow(); }
    }));
    bar.add(buttons, BorderLayout.WEST);

    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    right.add(status);
    right.add(button("close", new Runnable() {
      public void run() { dispose(); }
    }));
    bar.add(right, BorderLayout.EAST);

    return bar;
  }

  private JButton button(String key, final Runnable action) {
    JButton b = new JButton(Strings.get(key));
    b.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) { action.run(); }
    });
    return b;
  }

  // ---------------------------------------------------------------- loading

  /**
   * Re-read every preset row for this budget and rebuild the picker.
   *
   * @param select the preset to land on, or null to keep whatever is showing
   */
  private void reload(final String select) {
    final String wanted = select != null ? select : (String)presetPicker.getSelectedItem();

    run("Reading presets", new Work() {
      public void run(BudgetPortal portal) throws AbortException {
        List<AllocationPreset> rows = new HibernatePortal()
            .detachedCriteriaQueryList(CriteriaFactory.getInstance().getPresetsForBudget(budget));
        if (rows == null) rows = new ArrayList<AllocationPreset>();

        // the transaction form's Use menu reads this, so it moves with the editor
        State.getInstance().setPresets(rows);

        final List<AllocationPreset> all = rows;
        SwingUtilities.invokeLater(new Runnable() {
          public void run() { rebuild(all, wanted); }
        });
      }
    });
  }

  private void rebuild(List<AllocationPreset> all, String wanted) {
    loading = true;
    try {
      presetPicker.removeAllItems();
      for (String name : State.getInstance().getPresetNames()) presetPicker.addItem(name);
      if (wanted != null) presetPicker.setSelectedItem(wanted);
      else if (presetPicker.getItemCount() > 0) presetPicker.setSelectedIndex(0);
    } finally {
      loading = false;
    }
    showRowsFor((String)presetPicker.getSelectedItem());
  }

  private void showRowsFor(String name) {
    List<AllocationPreset> rows = new ArrayList<AllocationPreset>();
    if (name != null)
      for (AllocationPreset p : State.getInstance().getPresets())
        if (name.equals(p.getName())) rows.add(p);
    model.setRows(rows);
    installEditors();
  }

  /** The category editor needs the budget's categories, fetched once. */
  private void installEditors() {
    if (categories.isEmpty()) {
      run("Reading categories", new Work() {
        public void run(BudgetPortal portal) throws AbortException {
          List<Category> found = new HibernatePortal().detachedCriteriaQueryList(
              CriteriaFactory.getInstance().getCategoriesforBudget(budget));
          if (found != null) categories.addAll(found);
          SwingUtilities.invokeLater(new Runnable() {
            public void run() { attachEditors(); }
          });
        }
      });
    }
    else attachEditors();
  }

  private void attachEditors() {
    if (categories.isEmpty()) return;
    table.getColumnModel().getColumn(0).setCellEditor(
        new DefaultCellEditor(new JComboBox<Category>(categories.toArray(new Category[0]))));
    table.getColumnModel().getColumn(2).setCellEditor(
        new DefaultCellEditor(new JComboBox<AllocationPreset.AllocationType>(
            AllocationPreset.AllocationType.values())));
  }

  // ---------------------------------------------------------------- actions

  private void newPreset() {
    String name = ask("new.preset", "");
    if (name == null || name.trim().isEmpty()) return;
    if (categories.isEmpty()) return;

    // a preset is its rows, so a new one starts with a row or it does not exist
    final AllocationPreset row = new AllocationPreset();
    row.setBudget(budget);
    row.setName(name.trim());
    row.setCategory(categories.get(0));
    row.setAllocation(BigDecimal.ZERO);
    row.setAllocationType(AllocationPreset.AllocationType.fixed);
    row.setAutoDeduct(false);

    final String landOn = name.trim();
    run("Creating preset", new Work() {
      public void run(BudgetPortal portal) throws AbortException {
        new HibernatePortal().saveOrUpdate(row);
        reload(landOn);
      }
    });
  }

  private void renamePreset() {
    final String current = (String)presetPicker.getSelectedItem();
    if (current == null) return;
    String name = ask("rename.preset", current);
    if (name == null || name.trim().isEmpty() || name.trim().equals(current)) return;

    final String wanted = name.trim();
    run("Renaming preset", new Work() {
      public void run(BudgetPortal portal) throws AbortException {
        portal.renamePresetNamed(budget.getId(), current, wanted);
        reload(wanted);
      }
    });
  }

  private void deletePreset() {
    final String current = (String)presetPicker.getSelectedItem();
    if (current == null) return;
    if (JOptionPane.showConfirmDialog(this,
        MessageFormat.format(Strings.get("confirm.delete.preset"), current),
        Strings.get("delete.preset"),
        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;

    run("Deleting preset", new Work() {
      public void run(BudgetPortal portal) throws AbortException {
        portal.deletePresetNamed(budget.getId(), current);
        reload(null);
      }
    });
  }

  private void addRow() {
    final String current = (String)presetPicker.getSelectedItem();
    if (current == null || categories.isEmpty()) return;

    final AllocationPreset row = new AllocationPreset();
    row.setBudget(budget);
    row.setName(current);
    row.setCategory(categories.get(0));
    row.setAllocation(BigDecimal.ZERO);
    row.setAllocationType(AllocationPreset.AllocationType.fixed);
    row.setAutoDeduct(false);

    run("Adding preset row", new Work() {
      public void run(BudgetPortal portal) throws AbortException {
        new HibernatePortal().saveOrUpdate(row);
        reload(current);
      }
    });
  }

  private void deleteRow() {
    int selected = table.getSelectedRow();
    if (selected < 0) return;
    final AllocationPreset row = model.rowAt(selected);
    if (row == null || row.getId() == null) return;
    final String current = (String)presetPicker.getSelectedItem();

    run("Deleting preset row", new Work() {
      public void run(BudgetPortal portal) throws AbortException {
        portal.deletePresetRow(row.getId());
        reload(current);
      }
    });
  }

  /**
   * Write one edited row back, one save at a time per row.  A row is re-sent once
   * if it changed again while its save was in the air, and only if that save
   * worked -- after a failure the row is out of step with the database and
   * re-sending the same object would fail the same way.
   */
  private void save(final AllocationPreset row) {
    synchronized (savesInFlight) {
      if (savesInFlight.containsKey(row)) {
        savesInFlight.put(row, Boolean.TRUE);
        return;
      }
      savesInFlight.put(row, Boolean.FALSE);
    }

    setStatus(Strings.get("save.pending"));
    ThreadPool.getInstance().execute(new StatusRunnable("Saving preset row") {
      public void run() {
        boolean ok = false;
        try {
          AllocationPreset saved = new HibernatePortal().saveOrUpdate(row);
          // the generated id on an insert, the new version stamp on an update:
          // without carrying both forward the next edit of this row would insert
          // a duplicate or be refused as stale
          row.setId(saved.getId());
          row.setStamp(saved.getStamp());
          ok = true;
          setStatus(" ");
        } catch (AbortException e) {
          setStatus(Strings.get("save.failed"));
        } finally {
          boolean again;
          synchronized (savesInFlight) {
            again = Boolean.TRUE.equals(savesInFlight.remove(row));
          }
          if (again && ok) save(row);
        }
      }
    });
  }

  private String ask(String titleKey, String initial) {
    return (String)JOptionPane.showInputDialog(this, Strings.get("preset.name"),
        Strings.get(titleKey), JOptionPane.PLAIN_MESSAGE, null, null, initial);
  }

  // ------------------------------------------------------------- plumbing

  /** One unit of work against the server, run off the event thread. */
  private interface Work {
    void run(BudgetPortal portal) throws AbortException;
  }

  private void run(final String what, final Work work) {
    setStatus(what + "…");
    ThreadPool.getInstance().execute(new StatusRunnable(what) {
      public void run() {
        try {
          work.run(new BudgetPortal());
          setStatus(" ");
        } catch (AbortException e) {
          // Portal has already put the reason in front of the user
          setStatus(Strings.get("save.failed"));
        }
      }
    });
  }

  private void setStatus(final String text) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() { status.setText(text); }
    });
  }

  // ------------------------------------------------------------ the table

  private class RowTableModel extends AbstractTableModel {
    private List<AllocationPreset> rows = new ArrayList<AllocationPreset>();

    void setRows(List<AllocationPreset> rows) {
      this.rows = rows;
      fireTableDataChanged();
    }

    AllocationPreset rowAt(int row) {
      return (row >= 0 && row < rows.size()) ? rows.get(row) : null;
    }

    public int getRowCount() { return rows.size(); }
    public int getColumnCount() { return 4; }

    @Override public String getColumnName(int column) {
      switch (column) {
        case 0: return Strings.get("category");
        case 1: return Strings.get("amount");
        case 2: return Strings.get("type");
        default: return Strings.get("auto.deduct");
      }
    }

    @Override public Class<?> getColumnClass(int column) {
      switch (column) {
        case 0: return Category.class;
        case 2: return AllocationPreset.AllocationType.class;
        case 3: return Boolean.class;
        default: return String.class;
      }
    }

    @Override public boolean isCellEditable(int row, int column) { return true; }

    public Object getValueAt(int row, int column) {
      AllocationPreset p = rows.get(row);
      switch (column) {
        case 0: return p.getCategory();
        case 1:
          // one column carries both a rate and an amount of money, so it is shown
          // as the plain number it is -- the type column says which it means
          BigDecimal v = p.getAllocation();
          return v == null ? "0" : v.stripTrailingZeros().toPlainString();
        case 2: return p.getAllocationType();
        default: return Boolean.TRUE.equals(p.isAutoDeduct());
      }
    }

    @Override public void setValueAt(Object value, int row, int column) {
      if (value == null) return;
      AllocationPreset p = rows.get(row);

      switch (column) {
        case 0:
          if (value.equals(p.getCategory())) return;
          p.setCategory((Category)value);
          break;
        case 1:
          BigDecimal parsed;
          try {
            parsed = new BigDecimal(value.toString().trim());
          } catch (NumberFormatException e) {
            return;   // leave the old value showing
          }
          if (p.getAllocation() != null && parsed.compareTo(p.getAllocation()) == 0) return;
          p.setAllocation(parsed);
          break;
        case 2:
          if (value.equals(p.getAllocationType())) return;
          p.setAllocationType((AllocationPreset.AllocationType)value);
          break;
        default:
          if (value.equals(Boolean.TRUE.equals(p.isAutoDeduct()))) return;
          p.setAutoDeduct((Boolean)value);
          break;
      }

      fireTableRowsUpdated(row, row);
      save(p);
    }
  }
}
