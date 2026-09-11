package net.lump.envelope.client.ui.components.forms.preferences;

import net.lump.envelope.client.portal.AdminPortal;
import net.lump.envelope.client.portal.BudgetPortal;
import net.lump.envelope.client.thread.StatusRunnable;
import net.lump.envelope.client.thread.ThreadPool;
import net.lump.envelope.client.ui.defs.Strings;
import net.lump.envelope.shared.command.security.Permission;
import net.lump.envelope.shared.entity.Budget;
import net.lump.envelope.shared.entity.User;
import net.lump.envelope.shared.exception.AbortException;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Admin tab of the Preferences dialog: users, which budget each works in,
 * who is an administrator, and passwords.
 *
 * <p>What it shows depends on who is logged in, but that is only what it
 * <em>draws</em>.  The server decides what is allowed: an administrator sees
 * everyone and may change anything; anyone else sees their own row, may change
 * nothing on it, and may set their own password.  The panel asks the server who
 * it is talking to each time it is refreshed rather than remembering, because
 * the answer changes at every login.
 *
 * <p>Passwords are hashed on the client, with a fresh salt, before they go
 * anywhere -- see AdminPortal.hash.
 *
 * @author troy
 */
public class AdminPanel extends JPanel {

  private static final long BASE = Permission.READ | Permission.WRITE;

  private final UserTableModel model = new UserTableModel();
  private final JTable table = new JTable(model);
  private final JButton newUser = new JButton(Strings.get("new.user"));
  private final JButton setPassword = new JButton(Strings.get("set.password"));
  private final JButton budgets = new JButton(Strings.get("budgets") + "…");
  private final JButton refresh = new JButton(Strings.get("refresh"));
  private final JLabel status = new JLabel(" ");

  /** the caller, as the server last described them; null until refreshed */
  private User me;
  private boolean admin;
  private List<Budget> budgetList = new ArrayList<Budget>();

  public AdminPanel() {
    super(new BorderLayout(6, 6));
    setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    table.setRowHeight(table.getRowHeight() + 4);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.putClientProperty("terminateEditOnFocusLost", true);
    add(new JScrollPane(table), BorderLayout.CENTER);

    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    left.add(newUser);
    left.add(setPassword);
    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    right.add(budgets);
    right.add(refresh);
    JPanel bar = new JPanel(new BorderLayout());
    bar.add(left, BorderLayout.WEST);
    bar.add(right, BorderLayout.EAST);

    JPanel south = new JPanel(new BorderLayout(0, 4));
    south.add(bar, BorderLayout.NORTH);
    south.add(status, BorderLayout.SOUTH);
    add(south, BorderLayout.SOUTH);

    newUser.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) { newUser(); }
    });
    setPassword.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) { setPasswordForSelected(); }
    });
    budgets.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) { editBudgets(); }
    });
    refresh.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) { refresh(); }
    });

    showNothing(Strings.get("admin.log.in.first"));
  }

  // ---------------------------------------------------------------- loading

  /** Ask the server who we are and, if an administrator, for everyone else. */
  public void refresh() {
    run("Reading users", new Work() {
      public void run(AdminPortal portal) throws AbortException {
        final User who = portal.whoAmI();
        final boolean isAdmin = who.getPermission().hasPermission(Permission.ADMIN);
        final List<User> users = isAdmin ? portal.listUsers() : new ArrayList<User>();
        final List<Budget> all = isAdmin ? portal.listBudgets() : new ArrayList<Budget>();
        if (!isAdmin) users.add(who);

        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            me = who;
            admin = isAdmin;
            budgetList = all;
            model.setUsers(users);
            attachEditors();
            newUser.setEnabled(admin);
            budgets.setEnabled(admin);
            setPassword.setEnabled(true);
            status.setText(admin
                ? MessageFormat.format(Strings.get("admin.status.admin"), users.size())
                : Strings.get("admin.status.user"));
          }
        });
      }
    });
  }

  /** Nothing to show, and a line saying why. */
  private void showNothing(String why) {
    me = null;
    admin = false;
    model.setUsers(new ArrayList<User>());
    newUser.setEnabled(false);
    setPassword.setEnabled(false);
    budgets.setEnabled(false);
    status.setText(why);
  }

  private void attachEditors() {
    // the budget column picks from every budget; the editor is only reached
    // when the model says the cell is editable, which it never does for a
    // non-administrator
    table.getColumnModel().getColumn(2).setCellEditor(
        new DefaultCellEditor(new JComboBox<Budget>(budgetList.toArray(new Budget[0]))));
  }

  // ---------------------------------------------------------------- actions

  private void newUser() {
    if (!admin) return;

    JTextField login = new JTextField(16);
    JTextField realName = new JTextField(16);
    JComboBox<Budget> budget = new JComboBox<Budget>(budgetList.toArray(new Budget[0]));
    JCheckBox isAdmin = new JCheckBox();
    JPasswordField password = new JPasswordField(16);
    JPasswordField again = new JPasswordField(16);

    JPanel form = new JPanel(new GridLayout(0, 2, 6, 4));
    form.add(new JLabel(Strings.get("username")));   form.add(login);
    form.add(new JLabel(Strings.get("real.name")));  form.add(realName);
    form.add(new JLabel(Strings.get("budget")));     form.add(budget);
    form.add(new JLabel(Strings.get("administrator"))); form.add(isAdmin);
    form.add(new JLabel(Strings.get("password")));   form.add(password);
    form.add(new JLabel(Strings.get("password.again"))); form.add(again);

    if (JOptionPane.showConfirmDialog(this, form, Strings.get("new.user"),
        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

    final String pw = passwordIfMatching(password, again);
    if (pw == null) return;
    final String name = login.getText().trim();
    final String real = realName.getText().trim();
    final Budget chosen = (Budget)budget.getSelectedItem();
    final long permissions = BASE | (isAdmin.isSelected() ? Permission.ADMIN : 0L);
    if (name.isEmpty() || chosen == null) return;

    run("Creating user", new Work() {
      public void run(AdminPortal portal) throws AbortException, NoSuchAlgorithmException {
        portal.createUser(name, real, chosen.getId(), permissions, pw);
        refresh();
      }
    });
  }

  private void setPasswordForSelected() {
    int row = table.getSelectedRow();
    final User target = row < 0 ? me : model.userAt(row);
    if (target == null) return;
    // a non-administrator's table holds only themself, but say so anyway
    if (!admin && !target.getId().equals(me.getId())) return;

    JPasswordField password = new JPasswordField(16);
    JPasswordField again = new JPasswordField(16);
    JPanel form = new JPanel(new GridLayout(0, 2, 6, 4));
    form.add(new JLabel(Strings.get("password")));       form.add(password);
    form.add(new JLabel(Strings.get("password.again"))); form.add(again);

    if (JOptionPane.showConfirmDialog(this, form,
        MessageFormat.format(Strings.get("set.password.for"), target.getName()),
        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

    final String pw = passwordIfMatching(password, again);
    if (pw == null) return;

    run("Setting password", new Work() {
      public void run(AdminPortal portal) throws AbortException, NoSuchAlgorithmException {
        portal.setPassword(target.getId(), pw);
        setStatus(MessageFormat.format(Strings.get("password.set.for"), target.getName()));
      }
    });
  }

  /** The password the two fields agree on, or null after telling the user why not. */
  private String passwordIfMatching(JPasswordField a, JPasswordField b) {
    char[] one = a.getPassword(), two = b.getPassword();
    try {
      if (one.length == 0) {
        JOptionPane.showMessageDialog(this, Strings.get("error.password.empty"),
            Strings.get("error"), JOptionPane.ERROR_MESSAGE);
        return null;
      }
      if (!Arrays.equals(one, two)) {
        JOptionPane.showMessageDialog(this, Strings.get("error.passwords.differ"),
            Strings.get("error"), JOptionPane.ERROR_MESSAGE);
        return null;
      }
      return new String(one);
    } finally {
      Arrays.fill(one, '\0');
      Arrays.fill(two, '\0');
    }
  }

  private void editBudgets() {
    if (!admin) return;
    new BudgetListDialog(SwingUtilities.getWindowAncestor(this)).setVisible(true);
    refresh();   // names may have changed, budgets may have come or gone
  }

  // ------------------------------------------------------------- plumbing

  private interface Work {
    void run(AdminPortal portal) throws AbortException, NoSuchAlgorithmException;
  }

  private void run(final String what, final Work work) {
    setStatus(what + "…");
    ThreadPool.getInstance().execute(new StatusRunnable(what) {
      public void run() {
        try {
          work.run(new AdminPortal());
        } catch (AbortException e) {
          // Portal has already put the reason in front of the user
          setStatus(Strings.get("save.failed"));
        } catch (NoSuchAlgorithmException e) {
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

  private class UserTableModel extends AbstractTableModel {
    private List<User> users = new ArrayList<User>();

    void setUsers(List<User> users) {
      this.users = users;
      fireTableDataChanged();
    }

    User userAt(int row) {
      return (row >= 0 && row < users.size()) ? users.get(row) : null;
    }

    public int getRowCount() { return users.size(); }
    public int getColumnCount() { return 4; }

    @Override public String getColumnName(int column) {
      switch (column) {
        case 0: return Strings.get("username");
        case 1: return Strings.get("real.name");
        case 2: return Strings.get("budget");
        default: return Strings.get("administrator");
      }
    }

    @Override public Class<?> getColumnClass(int column) {
      switch (column) {
        case 2: return Budget.class;
        case 3: return Boolean.class;
        default: return String.class;
      }
    }

    /**
     * Only an administrator edits anything, and the login name is never edited:
     * it is the identity the password hash and the session are bound to.
     */
    @Override public boolean isCellEditable(int row, int column) {
      return admin && column != 0;
    }

    public Object getValueAt(int row, int column) {
      User u = users.get(row);
      switch (column) {
        case 0: return u.getName();
        case 1: return u.getRealName();
        case 2: return u.getBudget();
        default: return u.getPermission() != null && u.getPermission().hasPermission(Permission.ADMIN);
      }
    }

    @Override public void setValueAt(Object value, int row, int column) {
      if (value == null || !admin) return;
      final User u = users.get(row);

      String realName = u.getRealName();
      Budget budget = u.getBudget();
      long permissions = u.getPermission() == null ? BASE : u.getPermission().toLong();

      switch (column) {
        case 1:
          if (value.toString().trim().equals(realName)) return;
          realName = value.toString().trim();
          break;
        case 2:
          if (budget != null && budget.getId().equals(((Budget)value).getId())) return;
          budget = (Budget)value;
          break;
        default:
          boolean wantAdmin = Boolean.TRUE.equals(value);
          if (wantAdmin == ((permissions & Permission.ADMIN) != 0)) return;
          // keep whatever else is set; only the ADMIN bit moves, and READ|WRITE
          // is always present so a user is never left able to do nothing
          permissions = (permissions | BASE);
          permissions = wantAdmin ? (permissions | Permission.ADMIN)
                                  : (permissions & ~Permission.ADMIN);
          break;
      }

      final String newReal = realName;
      final Budget newBudget = budget;
      final long newPermissions = permissions;
      run("Updating user", new Work() {
        public void run(AdminPortal portal) throws AbortException {
          portal.updateUser(u.getId(), newReal, newBudget.getId(), newPermissions);
          refresh();   // re-read rather than guess; the server may have refused
        }
      });
    }
  }

  // ------------------------------------------------------- budget list dialog

  /** Add, rename and remove budgets. */
  private class BudgetListDialog extends JDialog {
    private final DefaultListModel<Budget> listModel = new DefaultListModel<Budget>();
    private final JList<Budget> list = new JList<Budget>(listModel);

    BudgetListDialog(Window owner) {
      super(owner, Strings.get("budgets"), ModalityType.APPLICATION_MODAL);
      setLayout(new BorderLayout(6, 6));
      ((JComponent)getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

      list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      add(new JScrollPane(list), BorderLayout.CENTER);

      JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
      buttons.add(button("new.budget", new Runnable() { public void run() { create(); } }));
      buttons.add(button("rename.budget", new Runnable() { public void run() { rename(); } }));
      buttons.add(button("delete.budget", new Runnable() { public void run() { remove(); } }));
      JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
      right.add(button("close", new Runnable() { public void run() { dispose(); } }));
      JPanel bar = new JPanel(new BorderLayout());
      bar.add(buttons, BorderLayout.WEST);
      bar.add(right, BorderLayout.EAST);
      add(bar, BorderLayout.SOUTH);

      setPreferredSize(new Dimension(420, 300));
      pack();
      setLocationRelativeTo(owner);
      load();
    }

    private JButton button(String key, final Runnable action) {
      JButton b = new JButton(Strings.get(key));
      b.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) { action.run(); }
      });
      return b;
    }

    private void load() {
      AdminPanel.this.run("Reading budgets", new Work() {
        public void run(AdminPortal portal) throws AbortException {
          final List<Budget> all = portal.listBudgets();
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              listModel.clear();
              for (Budget b : all) listModel.addElement(b);
            }
          });
        }
      });
    }

    private void create() {
      final String name = JOptionPane.showInputDialog(this, Strings.get("name"),
          Strings.get("new.budget"), JOptionPane.PLAIN_MESSAGE);
      if (name == null || name.trim().isEmpty()) return;
      AdminPanel.this.run("Creating budget", new Work() {
        public void run(AdminPortal portal) throws AbortException {
          portal.createBudget(name.trim());
          load();
        }
      });
    }

    private void rename() {
      final Budget b = list.getSelectedValue();
      if (b == null) return;
      final String name = (String)JOptionPane.showInputDialog(this, Strings.get("name"),
          Strings.get("rename.budget"), JOptionPane.PLAIN_MESSAGE, null, null, b.getName());
      if (name == null || name.trim().isEmpty() || name.trim().equals(b.getName())) return;
      AdminPanel.this.run("Renaming budget", new Work() {
        public void run(AdminPortal portal) throws AbortException {
          new BudgetPortal().renameBudget(b.getId(), name.trim());
          load();
        }
      });
    }

    private void remove() {
      final Budget b = list.getSelectedValue();
      if (b == null) return;
      if (JOptionPane.showConfirmDialog(this,
          MessageFormat.format(Strings.get("confirm.delete.budget"), b.getName()),
          Strings.get("delete.budget"),
          JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
      AdminPanel.this.run("Deleting budget", new Work() {
        public void run(AdminPortal portal) throws AbortException {
          portal.deleteBudget(b.getId());
          load();
        }
      });
    }
  }
}
