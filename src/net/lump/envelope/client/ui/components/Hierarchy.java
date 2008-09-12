package us.lump.envelope.client.ui.components;

import us.lump.envelope.client.CriteriaFactory;
import us.lump.envelope.client.State;
import us.lump.envelope.client.thread.EnvelopeRunnable;
import us.lump.envelope.client.thread.ThreadPool;
import us.lump.envelope.client.ui.MainFrame;
import us.lump.envelope.client.ui.components.forms.TableQueryBar;
import us.lump.envelope.client.ui.components.models.TransactionTableModel;
import us.lump.envelope.client.ui.defs.Colors;
import us.lump.envelope.client.ui.defs.Fonts;
import us.lump.envelope.client.ui.defs.Strings;
import static us.lump.envelope.client.ui.images.ImageResource.icon.*;
import us.lump.envelope.entity.Account;
import us.lump.envelope.entity.Budget;
import us.lump.lib.Money;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Date;


/**
 * The hierarchy of budget, account, categories.
 *
 * @author Troy Bowman
 * @version $Id: Hierarchy.java,v 1.17 2008/09/12 00:27:49 troy Exp $
 */
public class Hierarchy extends JTree {
  private static Hierarchy singleton;
  private final State state = State.getInstance();
  private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
  TransactionTableModel tm;

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

  private Hierarchy() {
    super();

    setModel(new DefaultTreeModel(rootNode, false));

    getSelectionModel().setSelectionMode(
        TreeSelectionModel.SINGLE_TREE_SELECTION);

    TreeCellRenderer renderer = new TreeCellRenderer();
    renderer.setLeafIcon(envelope.get());
    renderer.setOpenIcon(account.get());
    renderer.setClosedIcon(account_closed.get());
    setCellRenderer(renderer);

    addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(final TreeSelectionEvent e) {
        
        DefaultMutableTreeNode node =
            (DefaultMutableTreeNode)e.getPath().getLastPathComponent();
        final Object o = node.getUserObject();

        if (o instanceof Account
            || o instanceof CategoryTotal) {

          final TableQueryBar tqb = TableQueryBar.getInstance();
          sanifyDates(tqb);

          if (tm == null) tm = new TransactionTableModel(
              o, tqb.getBeginDate(), tqb.getEndDate(),
              tqb.getTable());
          else tm.queue(o, tqb.getBeginDate(), tqb.getEndDate());

          JTable table = tqb.getTable();
          table.getTableHeader().setUpdateTableInRealTime(true);
          table.setModel(tm);

          table.setDefaultRenderer(Money.class, new MoneyRenderer());
          table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
          table.getTableHeader().setReorderingAllowed(false);
          table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

          tqb.setTitleLabel(o.toString());
          tqb.setTitleIcon(getIconForObject(o, true));

          MainFrame.getInstance()
              .setContentPane(tqb.getTableQueryPanel());
          tqb.setViewportView(table);

          Dimension checkWidth = new JCheckBox().getPreferredSize();
          int dateWidth =
              table.getFontMetrics(table.getFont()).stringWidth("MMM MM, MMMM");
          int amountWidth = table.getFontMetrics(Fonts.getFont("fixed"))
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
            }
          });
        }
//        else {
//          MainFrame.getInstance().setContentPane(null);
//        }
      }
    });
  }

  public void refreshTree(Budget budget) {
    rootNode.setUserObject(budget);

    EnvelopeRunnable r = new EnvelopeRunnable(Strings.get("building.tree")) {
      public void run() {
        for (Account a : CriteriaFactory.getInstance()
            .getAccountsForBudget(state.getBudget()))
          state.getAccounts().add(a);

        for (Account a : state.getAccounts()) {
          DefaultMutableTreeNode thisNode = new DefaultMutableTreeNode(a);
          for (CategoryTotal ca :
              CriteriaFactory.getInstance().getCategoriesForAccount(a)) {
            thisNode.add(new DefaultMutableTreeNode(ca));
          }
          rootNode.add(thisNode);
        }

        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            singleton.expandPath(new TreePath(rootNode));
            singleton.repaint();
            RepaintManager.currentManager(singleton).paintDirtyRegions();
          }
        });
      }
    };
    ThreadPool.getInstance().execute(r);
  }

  class MoneyRenderer extends DefaultTableCellRenderer {
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
      label.setFont(Fonts.getFont("fixed"));
      label.setBorder(
          new CompoundBorder(
              new EmptyBorder(new Insets(1, 4, 1, 4)),
              label.getBorder()));

      if (value != null && ((Money)value).doubleValue() < 0)
        label.setForeground(Colors.getColor("red"));
//      else
//        label.setForeground(Colors.getColor("green"));

      if (isSelected) {
        label.setBackground(table.getSelectionBackground());
        label.setForeground(table.getSelectionForeground());
        label.setOpaque(true);
      }
      if (hasFocus) {
        label.setForeground(table.getSelectionForeground());
        label.setBackground(table.getSelectionBackground());
        label.setOpaque(true);
      }

      return label;
    }
  }

  class TreeCellRenderer extends javax.swing.tree.DefaultTreeCellRenderer {

    public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                  boolean sel,
                                                  boolean expanded,
                                                  boolean leaf, int row,
                                                  boolean hasFocus) {
      this.hasFocus = hasFocus;
      String name = tree.convertValueToText(
          value, sel, expanded, leaf, row, hasFocus);

      if (sel) setForeground(getTextSelectionColor());
      else setForeground(getTextNonSelectionColor());
      setText(name);

      Icon icon = null;
      if (value != null
          && value instanceof DefaultMutableTreeNode
          && ((DefaultMutableTreeNode)value).getUserObject() != null) {
        icon = getIconForObject(
            ((DefaultMutableTreeNode)value).getUserObject(), expanded);
      }

      if (icon != null) {
        setIcon(icon);
      } else {
        if (leaf) {
          setIcon(getLeafIcon());
        } else if (expanded) {
          setIcon(getOpenIcon());
        } else {
          setIcon(getClosedIcon());
        }
      }


      setComponentOrientation(tree.getComponentOrientation());

      selected = sel;

      return this;
    }
  }

  private Icon getIconForObject(Object o, boolean expanded) {
    if (o instanceof Budget) {
      if (expanded) return budget.get();
      else return budget_closed.get();
    } else if (o instanceof Account) {
      if (expanded) return account.get();
      else return account_closed.get();
    } else if (o instanceof CategoryTotal) {
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

  public static class CategoryTotal {
    public String name;
    public Integer id;
    public Money balance;

    public CategoryTotal(String name, Integer id, Money balance) {
      this.name = name;
      this.id = id;
      this.balance = balance;
    }

    public String toString() { return name; }
  }

}
