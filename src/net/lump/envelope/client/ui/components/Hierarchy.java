package net.lump.envelope.client.ui.components;

import net.lump.envelope.client.CriteriaFactory;
import net.lump.envelope.client.State;
import net.lump.envelope.client.portal.BudgetPortal;
import net.lump.envelope.client.thread.StatusRunnable;
import net.lump.envelope.client.thread.ThreadPool;
import net.lump.envelope.client.ui.components.forms.table_query_bar.TableQueryBar;
import net.lump.envelope.client.ui.components.forms.transaction.MoneyRenderer;
import net.lump.envelope.client.ui.defs.Colors;
import net.lump.envelope.client.ui.defs.Strings;
import net.lump.envelope.shared.entity.Account;
import net.lump.envelope.shared.entity.Budget;
import net.lump.envelope.shared.entity.Category;
import net.lump.envelope.shared.exception.AbortException;
import net.lump.lib.Money;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static net.lump.envelope.client.ui.images.ImageResource.icon.*;


/**
 * The hierarchy of budget, account, categories.
 *
 * @author Troy Bowman
 * @version $Id: Hierarchy.java,v 1.36 2010/09/22 19:27:37 troy Exp $
 */
public class Hierarchy extends JTree {
  private static Hierarchy singleton;
  private final State state = State.getInstance();
  private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
  TransactionTableModel tm;
  private static DefaultTreeModel treeModel;
  final WideTreeUI wtui = new WideTreeUI();

  public static Hierarchy getInstance() {
    if (singleton == null) singleton = new Hierarchy();
    return singleton;
  }

  private void sanifyDates(TableQueryBar tqb) {
    if (tqb.getBeginDate().after(tqb.getEndDate())) {
      Date temp = tqb.getBeginDate();
      tqb.setBeginDate(tqb.getEndDate());
      tqb.setEndDate(temp);
    }
  }

  public void configureLayoutCache() {
    wtui.configureLayoutCache();
  }

  private Hierarchy() {
    super();

    setUI(wtui);
    addComponentListener(new ComponentListener() {

      public void componentResized(ComponentEvent e) {
        configureLayoutCache();
      }

      public void componentMoved(ComponentEvent e) {
      }

      public void componentShown(ComponentEvent e) {
      }

      public void componentHidden(ComponentEvent e) {}
    });

    treeModel = new DefaultTreeModel(rootNode, false);
    setModel(treeModel);
//    setRootVisible(false);

    getSelectionModel().setSelectionMode(
        TreeSelectionModel.SINGLE_TREE_SELECTION);

    // --- editing the shape of the budget ----------------------------------
    // Right-click selects the node under the pointer first, so the menu and the
    // rest of the window agree about what is being worked on.
    addMouseListener(new MouseAdapter() {
      @Override public void mousePressed(MouseEvent e) { showMenuIfTriggered(e); }
      @Override public void mouseReleased(MouseEvent e) { showMenuIfTriggered(e); }
      private void showMenuIfTriggered(MouseEvent e) {
        // the popup trigger is press on some platforms and release on others
        if (!e.isPopupTrigger()) return;
        int row = getRowForLocation(e.getX(), e.getY());
        if (row >= 0) setSelectionRow(row);

        Object node = getLastSelectedPathComponent();
        Object selected = (node instanceof DefaultMutableTreeNode)
            ? ((DefaultMutableTreeNode)node).getUserObject() : null;
        nodeMenu(selected).show(Hierarchy.this, e.getX(), e.getY());
      }
    });

    EnvelopeTreeCellRenderer renderer = new EnvelopeTreeCellRenderer();
//    renderer.setLeafIcon(envelope.get());
//    renderer.setOpenIcon(account.get());
//    renderer.setClosedIcon(account_closed.get());
    setCellRenderer(renderer);

    addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(final TreeSelectionEvent e) {

        DefaultMutableTreeNode node =
            (DefaultMutableTreeNode)e.getPath().getLastPathComponent();
        final Object o = node.getUserObject();

        if (o instanceof CategoryTotal) {

//          MainFrame.getInstance().setDetailPane(null);

          final TableQueryBar tqb = TableQueryBar.getInstance();
          sanifyDates(tqb);

          JTable table = tqb.getTable();
          if (tm == null)  tm = new TransactionTableModel(o, tqb.getBeginDate(), tqb.getEndDate(), tqb.getTable());

          else tm.queue(o, tqb.getBeginDate(), tqb.getEndDate());
          if (!table.getModel().equals(tm)) table.setModel(tm);

          table.setDefaultRenderer(Money.class, new MoneyRenderer());
          tqb.setTitleLabel(o.toString());
          tqb.setTitleIcon(getIconForObject(o, true));

          Dimension checkWidth = new JCheckBox().getPreferredSize();
          int dateWidth =
              table.getFontMetrics(table.getFont()).stringWidth("MMM MM, MMMM");
          //int amountWidth = table.getFontMetrics(Fonts.fixed.getFont())
          int amountWidth = table.getFontMetrics(table.getFont())
              .stringWidth("$0,000,000.00");
          table.getColumnModel().getColumn(0).setMaxWidth(checkWidth.width);

          Integer[] settings = new Integer[]{
              checkWidth.width,
              dateWidth,
              amountWidth,
              amountWidth,
              amountWidth
          };
          for (int x = 0; x < 5; x++) {
            table.getColumnModel().getColumn(x).setMinWidth(settings[x]);
            table.getColumnModel().getColumn(x).setMaxWidth(settings[x]);
          }

//          table.getTableHeader().setUpdateTableInRealTime(true);

          for (ActionListener a : tqb.getRefreshButton().getActionListeners())
            tqb.getRefreshButton().removeActionListener(a);

          tqb.getRefreshButton().addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
              sanifyDates(tqb);
              tm.queue(o, tqb.getBeginDate(), tqb.getEndDate());
              try {
                refreshTree(State.getInstance().getBudget());
              } catch (AbortException ignore) {}
            }
          });
        }
//        else {
//          MainFrame.getInstance().setTablePane(null);
//        }
      }
    });
  }

  /** One structure change, expressed against the portal that performs it. */
  private interface StructureTask {
    void run(BudgetPortal portal) throws AbortException;
  }

  /**
   * Run one structure change off the event thread, then rebuild the tree from the
   * server so the new shape and its balances arrive together.
   */
  private void structureChange(String status, final StructureTask task) {
    ThreadPool.getInstance().execute(new StatusRunnable(status) {
      public void run() {
        try {
          task.run(new BudgetPortal());
          refreshTree(State.getInstance().getBudget());
        } catch (AbortException e) {
          // Portal has already put the reason in front of the user
        }
      }
    });
  }

  private JMenuItem menuItem(String key, final Runnable action) {
    JMenuItem item = new JMenuItem(Strings.get(key));
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) { action.run(); }
    });
    return item;
  }

  private boolean confirm(String titleKey, String messageKey, String name) {
    return JOptionPane.showConfirmDialog(
        this,
        MessageFormat.format(Strings.get(messageKey), name),
        Strings.get(titleKey),
        JOptionPane.YES_NO_OPTION,
        JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
  }

  /**
   * Right-click menu for a tree node.  What it offers depends on what was clicked:
   * the budget takes accounts, an account takes categories, a category takes only
   * a rename or a removal.
   */
  private JPopupMenu nodeMenu(Object selected) {
    JPopupMenu menu = new JPopupMenu();

    // AccountTotal extends CategoryTotal, so the narrower test has to come first
    if (selected instanceof AccountTotal) {
      final Account account = ((AccountTotal)selected).account;
      // a transaction is started from here rather than from the transaction
      // table's rows, so an account with no transactions yet can still get one
      menu.add(menuItem("new.transaction", new Runnable() {
        public void run() { newTransactionIn(account); }
      }));
      menu.addSeparator();
      menu.add(menuItem("new.category", new Runnable() {
        public void run() { newCategory(account); }
      }));
      menu.addSeparator();
      menu.add(menuItem("rename.account", new Runnable() {
        public void run() { renameAccount(account); }
      }));
      menu.add(menuItem("delete.account", new Runnable() {
        public void run() { deleteAccount(account); }
      }));
    }
    else if (selected instanceof CategoryTotal) {
      final CategoryTotal category = (CategoryTotal)selected;
      menu.add(menuItem("new.transaction", new Runnable() {
        public void run() {
          TableQueryBar.getInstance().newTransaction(category.id);
        }
      }));
      menu.addSeparator();
      menu.add(menuItem("rename.category", new Runnable() {
        public void run() { renameCategory(category); }
      }));
      menu.add(menuItem("delete.category", new Runnable() {
        public void run() { deleteCategory(category); }
      }));
    }
    else {
      // the root, or nothing at all
      menu.add(menuItem("new.account", new Runnable() {
        public void run() { newAccount(); }
      }));
      if (selected instanceof Budget) {
        final Budget budget = (Budget)selected;
        menu.addSeparator();
        menu.add(menuItem("rename.budget", new Runnable() {
          public void run() { renameBudget(budget); }
        }));
      }
    }

    return menu;
  }

  /**
   * Start a transaction in an account, using its first category.
   *
   * <p>A transaction is reachable only through its allocations, so it needs a
   * category to exist at all; from an account node the first one stands in, and
   * the user can change it on the form.
   */
  private void newTransactionIn(final Account account) {
    ThreadPool.getInstance().execute(new StatusRunnable("Reading categories") {
      public void run() {
        try {
          final java.util.List<CategoryTotal> categories =
              CriteriaFactory.getInstance().getCategoriesForAccount(account);
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              TableQueryBar.getInstance().newTransaction(
                  (categories == null || categories.isEmpty()) ? null : categories.get(0).id);
            }
          });
        } catch (AbortException e) {
          // Portal has already put the reason in front of the user
        }
      }
    });
  }

  private void renameBudget(final Budget budget) {
    final String name = (String)JOptionPane.showInputDialog(
        this, Strings.get("name"), Strings.get("rename.budget"),
        JOptionPane.PLAIN_MESSAGE, null, null, budget.getName());
    if (name == null || name.trim().isEmpty() || name.trim().equals(budget.getName())) return;

    structureChange("Renaming budget", new StructureTask() {
      public void run(BudgetPortal portal) throws AbortException {
        portal.renameBudget(budget.getId(), name.trim());
        // the tree root shows the budget, and State holds the copy it is drawn
        // from, so that copy has to move too
        State.getInstance().getBudget().setName(name.trim());
      }
    });
  }

  private void newAccount() {
    JTextField name = new JTextField(20);
    JComboBox<Account.AccountType> type =
        new JComboBox<Account.AccountType>(Account.AccountType.values());
    JPanel panel = new JPanel(new GridLayout(0, 2, 4, 4));
    panel.add(new JLabel(Strings.get("name")));
    panel.add(name);
    panel.add(new JLabel(Strings.get("type")));
    panel.add(type);

    if (JOptionPane.showConfirmDialog(this, panel, Strings.get("new.account"),
        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
      return;

    final String accountName = name.getText();
    final Account.AccountType accountType = (Account.AccountType)type.getSelectedItem();
    structureChange("Creating account", new StructureTask() {
      public void run(BudgetPortal portal) throws AbortException {
        portal.createAccount(State.getInstance().getBudget().getId(), accountName, accountType);
      }
    });
  }

  private void renameAccount(final Account account) {
    final String name = (String)JOptionPane.showInputDialog(
        this, Strings.get("name"), Strings.get("rename.account"),
        JOptionPane.PLAIN_MESSAGE, null, null, account.getName());
    if (name == null) return;
    structureChange("Renaming account", new StructureTask() {
      public void run(BudgetPortal portal) throws AbortException {
        portal.renameAccount(account.getId(), name);
      }
    });
  }

  private void deleteAccount(final Account account) {
    if (!confirm("delete.account", "confirm.delete.account", account.getName())) return;
    structureChange("Deleting account", new StructureTask() {
      public void run(BudgetPortal portal) throws AbortException {
        portal.deleteAccount(account.getId());
      }
    });
  }

  private void newCategory(final Account account) {
    final String name = (String)JOptionPane.showInputDialog(
        this, Strings.get("name"), Strings.get("new.category"),
        JOptionPane.PLAIN_MESSAGE, null, null, "");
    if (name == null) return;
    structureChange("Creating category", new StructureTask() {
      public void run(BudgetPortal portal) throws AbortException {
        portal.createCategory(account.getId(), name);
      }
    });
  }

  private void renameCategory(final CategoryTotal category) {
    final String name = (String)JOptionPane.showInputDialog(
        this, Strings.get("name"), Strings.get("rename.category"),
        JOptionPane.PLAIN_MESSAGE, null, null, category.name);
    if (name == null) return;
    structureChange("Renaming category", new StructureTask() {
      public void run(BudgetPortal portal) throws AbortException {
        portal.renameCategory(category.id, name);
      }
    });
  }

  private void deleteCategory(final CategoryTotal category) {
    if (!confirm("delete.category", "confirm.delete.category", category.name)) return;
    structureChange("Deleting category", new StructureTask() {
      public void run(BudgetPortal portal) throws AbortException {
        portal.deleteCategory(category.id);
      }
    });
  }

  public void refreshTree(Budget budget) {
    rootNode.setUserObject(budget);


    StatusRunnable r = new StatusRunnable(Strings.get("updating.tree")) {

      Object selectedObject = null;

      private List getListFor(DefaultMutableTreeNode dmtn)
          throws AbortException {
        Object o = dmtn.getUserObject();

        if (o instanceof Budget)
          return CriteriaFactory.getInstance().getAccountTotals((Budget)o);
        if (o instanceof AccountTotal)
          return CriteriaFactory.getInstance()
              .getCategoriesForAccount(((AccountTotal)o).account);
        if (o instanceof Category)
          return null;
        else return null;
      }

      public void updateChildren(final DefaultMutableTreeNode node)
          throws AbortException {
        List children = getListFor(node);
        // An empty list is an answer, not a reason to stop.  Returning here left
        // whatever was already under the node in place, so a deleted account kept
        // its row and a new account -- which has no categories yet -- kept the
        // categories of whoever previously occupied its position.
        if (children == null) children = new ArrayList();

        // Trim surplus from the END.  Removing by ascending index while the list
        // shifts down underneath takes out every other node: remove(x) moves the
        // next one into x, and then x++ steps straight over it.
        while (node.getChildCount() > children.size()) {
          final int last = node.getChildCount() - 1;
          final Object[] gone = new Object[]{node.getChildAt(last)};
          node.remove(last);
          SwingUtilities.invokeLater(new Runnable() {
            public void run() { treeModel.nodesWereRemoved(node, new int[]{last}, gone); }
          });
        }

        for (int x = 0; x < children.size(); x++) {
          DefaultMutableTreeNode dmtn;
          if (x < node.getChildCount()) {
            dmtn = ((DefaultMutableTreeNode)node.getChildAt(x));

            // Nodes are reused by position, and accounts are listed by name, so
            // inserting one shifts every account after it onto a different node.
            // The children hanging off that node still belong to the previous
            // occupant, and have to go before the recursion below refills them.
            Object had = dmtn.getUserObject();
            final boolean replaced = had == null || !had.equals(children.get(x));
            if (replaced) dmtn.removeAllChildren();

            dmtn.setUserObject(children.get(x));
            final DefaultMutableTreeNode fdmtn = dmtn;
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                // a node whose children were just thrown away needs its structure
                // reloaded, not merely repainted
                if (replaced) treeModel.nodeStructureChanged(fdmtn);
                else treeModel.nodeChanged(fdmtn);
              }
            });
          }
          else {
            dmtn = new DefaultMutableTreeNode(children.get(x));
            node.add(dmtn);
            final int[] fx = new int[]{x};
            SwingUtilities.invokeLater(new Runnable() {
              public void run() { treeModel.nodesWereInserted(node, fx); }
            });
          }

          // Selection is Swing state, and setting it fires the selection listeners
          // that rebuild the transaction table model.  This ran on the pool worker,
          // so that rebuild happened off the EDT after every allocation save --
          // every neighbouring model notification in this method already defers.
          if (selectedObject != null
              && dmtn.getUserObject().equals(
              ((DefaultMutableTreeNode)selectedObject).getUserObject())) {
            final TreePath path = new TreePath(dmtn.getPath());
            SwingUtilities.invokeLater(new Runnable() {
              public void run() { singleton.setSelectionPath(path); }
            });
          }

          updateChildren(dmtn);
        }
      }

      public void run() {

        synchronized (rootNode) {
          try {
            // get the selected node, if any
            selectedObject = singleton.getLastSelectedPathComponent();

//        JScrollPane sp = getScrollPane();
            // get the view if any
//        viewPoint = sp == null ? null : sp.getViewport().getViewPosition();


            updateChildren(rootNode);
            SwingUtilities.invokeLater(new Runnable() {
              public void run() { treeModel.nodeChanged(rootNode); }
            });
//        RepaintManager.currentManager(singleton).isCompletelyDirty(singleton);

            if (!singleton.isExpanded(new TreePath(rootNode)))
              SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                  singleton.expandPath(new TreePath(rootNode));
//              RepaintManager.currentManager(singleton).isCompletelyDirty(singleton);
//              singleton.repaint();
//              RepaintManager.currentManager(singleton).paintDirtyRegions();
                }
              });
          } catch (AbortException ignore) {}
        }
      }
    };
    ThreadPool.getInstance().execute(r);
  }

  protected JScrollPane getScrollPane() {
    Component c = singleton;

    while (c != null && !(c instanceof JScrollPane)) c = c.getParent();
    if (c != null) return (JScrollPane)c;
    return null;
  }

  class EnvelopeTreeCellRenderer extends JComponent
      implements javax.swing.tree.TreeCellRenderer {
    BoxLayout layout = new BoxLayout(this, BoxLayout.X_AXIS);
    JLabel mainLabel = new JLabel();
    JLabel balanceLabel = new JLabel();

    Color textSelectionColor = UIManager.getColor("Tree.selectionForeground");
    Color textNonSelectionColor = UIManager.getColor("Tree.textForeground");
    Color backgroundSelectionColor =
        UIManager.getColor("Tree.selectionBackground");
    Color backgroundNonSelectionColor =
        UIManager.getColor("Tree.textBackground");
    Color borderSelectionColor =
        UIManager.getColor("Tree.selectionBorderColor");

    public EnvelopeTreeCellRenderer() {
      this.setLayout(layout);
      balanceLabel.setHorizontalAlignment(JLabel.RIGHT);
      this.add(mainLabel);
      this.add(new Box.Filler(new Dimension(5, 0), new Dimension(5, 0),
          new Dimension(Short.MAX_VALUE, 0)));
      this.add(balanceLabel);
//      this.setBorder(BorderFactory.createLineBorder(Color.BLACK));
//      balanceLabel.setBorder(BorderFactory.createLineBorder(Color.GREEN));
//      mainLabel.setBorder(BorderFactory.createLineBorder(Color.RED));
    }

    public Dimension getPreferredSize() {
      Dimension parent = super.getPreferredSize();

      Dimension mlpf = mainLabel.getPreferredSize();
      Dimension blpf = balanceLabel.getPreferredSize();

      Dimension out = new Dimension(
          Math.max(parent.width, mlpf.width + blpf.width),
          Math.max(parent.height, Math.max(mlpf.height, blpf.height)));

      return out;
    }

    protected void paintComponent(Graphics g) {
      if (ui != null) {
        // On the off chance some one created a UI, honor it
        super.paintComponent(g);
      }
      else if (isOpaque()) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
      }
    }

    public JLabel getMainLabel() {
      return mainLabel;
    }

    public Component getTreeCellRendererComponent(JTree tree,
        Object value,
        boolean selected,
        boolean expanded,
        boolean leaf,
        int row,
        boolean hasFocus) {

//      this.setSize(tree.getWidth(), this.getHeight());
      mainLabel.setFont(tree.getFont());
      balanceLabel.setFont(tree.getFont());
      if (selected || hasFocus) {
        this.setOpaque(true);
        this.setBackground(backgroundSelectionColor);
        mainLabel.setForeground(textSelectionColor);
        balanceLabel.setForeground(textSelectionColor);
      }
      else {
        this.setOpaque(false);
//        this.setBackground(backgroundNonSelectionColor);
        mainLabel.setForeground(textNonSelectionColor);
        balanceLabel.setForeground(textNonSelectionColor);
      }

      if (value != null
          && value instanceof DefaultMutableTreeNode
          && ((DefaultMutableTreeNode)value).getUserObject() != null) {

        Object o = ((DefaultMutableTreeNode)value).getUserObject();
        if (o instanceof Budget) {
          if (expanded) mainLabel.setIcon(budget.get());
          else mainLabel.setIcon(budget_closed.get());
          mainLabel.setText(((Budget)o).getName());
          balanceLabel.setText("");
        }
        else if (o instanceof CategoryTotal) {
          if (o instanceof AccountTotal) {
            if (expanded) mainLabel.setIcon(account.get());
            else mainLabel.setIcon(account_closed.get());
            mainLabel.setText(((AccountTotal)o).name);
            balanceLabel.setText("");
          }
          else {
            CategoryTotal ct = (CategoryTotal)o;

            double total = ct.balance.doubleValue();
            if (total < 0) mainLabel.setIcon(envelope_red.get());
            if (total == 0) mainLabel.setIcon(envelope_empty.get());
            if (total > 0 && total <= 100)
              mainLabel.setIcon(envelope_onebill.get());
            if (total > 100 && total <= 500) mainLabel.setIcon(envelope.get());
            if (total > 500 && total <= 1000)
              mainLabel.setIcon(envelope_full.get());
            if (total > 1000) mainLabel.setIcon(envelope_overflow.get());
          }
          mainLabel.setText(((CategoryTotal)o).name);
          balanceLabel.setText(((CategoryTotal)o).balance.toString());
          if (((CategoryTotal)o).balance.floatValue() < 0)
            balanceLabel.setForeground(Colors.getColor("red"));
        }
      }

      return this;
    }
  }

//  class TreeCellRenderer extends javax.swing.tree.DefaultTreeCellRenderer {
//
//    public Component getTreeCellRendererComponent(JTree tree, Object value,
//                                                  boolean sel,
//                                                  boolean expanded,
//                                                  boolean leaf, int row,
//                                                  boolean hasFocus) {
//      this.hasFocus = hasFocus;
//      String name = tree.convertValueToText(
//          value, sel, expanded, leaf, row, hasFocus);
//
//      if (sel) setForeground(getTextSelectionColor());
//      else setForeground(getTextNonSelectionColor());
//      setText(name);
//
//      Icon icon = null;
//      if (value != null
//          && value instanceof DefaultMutableTreeNode
//          && ((DefaultMutableTreeNode)value).getUserObject() != null) {
//        icon = getIconForObject(
//            ((DefaultMutableTreeNode)value).getUserObject(), expanded);
//      }
//
//      if (icon != null) {
//        setIcon(icon);
//      } else {
//        if (leaf) {
//          setIcon(getLeafIcon());
//        } else if (expanded) {
//          setIcon(getOpenIcon());
//        } else {
//          setIcon(getClosedIcon());
//        }
//      }
//
//
//      setComponentOrientation(tree.getComponentOrientation());
//
//      selected = sel;
//
//      return this;
//    }
//  }

  private Icon getIconForObject(Object o, boolean expanded) {
    if (o instanceof Budget) {
      if (expanded) return budget.get();
      else return budget_closed.get();
    }
    else if (o instanceof Account) {
      if (expanded) return account.get();
      else return account_closed.get();
    }
    else if (o instanceof CategoryTotal) {
      CategoryTotal ct = (CategoryTotal)o;
      double total = ct.balance.doubleValue();
      if (total < 0) return envelope_red.get();
      if (total == 0) return envelope_empty.get();
      if (total > 0 && total <= 100) return envelope_onebill.get();
      if (total > 100 && total <= 500) return envelope.get();
      if (total > 500 && total <= 1000) return envelope_full.get();
      if (total > 1000) return envelope_overflow.get();
    }
    return null;
  }

  public static class AccountTotal extends CategoryTotal {
    public Account account;

    public AccountTotal(Account account,
        String name,
        Integer id,
        Money balance) {
      super(name, id, balance);
      this.account = account;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof AccountTotal)) return false;
      return super.equals(o);
    }
  }

  public static class CategoryTotal {
    public String name;
    public Integer id;
    public Money balance;

    public CategoryTotal(String name, Integer id, Money balance) {
      this.name = name;
      this.id = id;
      this.balance = balance;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      CategoryTotal that = (CategoryTotal)o;
      if (id != null ? !id.equals(that.id) : that.id != null) return false;
      return true;
    }

    public String toString() { return name; }
  }

  public class WideTreeUI extends BasicTreeUI {
    HashMap<Integer, Integer> cachedwidth = new HashMap<Integer, Integer>();

    public WideTreeUI() {
      super();

    }

    public void configureLayoutCache() {
      super.configureLayoutCache();
    }

    protected void installDefaults() {
      super.installDefaults();
      // make the indexes more space efficient
      setRightChildIndent(Math.min(7, getRightChildIndent()));
      setLeftChildIndent(Math.min(5, getLeftChildIndent()));
    }

    @Override
    protected AbstractLayoutCache.NodeDimensions createNodeDimensions() {
      return new NodeDimensionsHandler() {
        @Override
        public Rectangle getNodeDimensions(Object value, int row,
            int depth, boolean expanded,
            Rectangle size) {

          // Return size of editing component, if editing and asking
          // for editing row.
          if (editingComponent != null && editingRow == row) {
            Dimension prefSize = editingComponent.
                getPreferredSize();
            int rh = getRowHeight();

            if (rh > 0 && rh != prefSize.height)
              prefSize.height = rh;
            if (size != null) {
              size.x = getRowX(row, depth);
              size.width = prefSize.width;
              size.height = prefSize.height;
            }
            else {
              size = new Rectangle(getRowX(row, depth), 0,
                  prefSize.width, prefSize.height);
            }
            return size;
          }
          // Not editing, use renderer.
          if (currentCellRenderer != null) {
            Component aComponent;

            aComponent = currentCellRenderer.getTreeCellRendererComponent
                (tree, value, tree.isRowSelected(row),
                    expanded, treeModel.isLeaf(value), row,
                    false);
            if (tree != null) {
              // Only ever removed when UI changes, this is OK!
              rendererPane.add(aComponent);
              aComponent.validate();
            }

            Dimension prefSize = aComponent.getPreferredSize();

            JScrollPane sp = getScrollPane();

            int targetWidth;
            if (sp == null) targetWidth = prefSize.width;
            else {
              int targetCellWidth =
                  sp.getViewportBorderBounds().width - getRowX(row, depth);
              if (!cachedwidth.containsKey(depth))
                cachedwidth.put(depth, targetCellWidth);
              else {
                if (cachedwidth.get(depth) != targetCellWidth) {
                  cachedwidth.put(depth, targetCellWidth);
                }
              }
              targetWidth = targetCellWidth;
            }

            if (size != null) {
              size.x = getRowX(row, depth);
              size.width = targetWidth;
              size.height = prefSize.height;
            }
            else {
              size = new Rectangle(
                  size.width = getRowX(row, depth), 0,
                  targetWidth, prefSize.height);
            }

            return size;
          }
          return null;

        }
      };
    }
  }

}
