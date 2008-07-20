package us.lump.envelope.client.ui;

import us.lump.envelope.client.CriteriaFactory;
import us.lump.envelope.client.State;
import us.lump.envelope.client.thread.EnvelopeRunnable;
import us.lump.envelope.client.thread.StatusElement;
import us.lump.envelope.client.thread.ThreadPool;
import us.lump.envelope.client.ui.defs.Colors;
import us.lump.envelope.client.ui.defs.Fonts;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.entity.Account;
import us.lump.envelope.entity.Budget;
import us.lump.envelope.entity.Category;
import us.lump.envelope.entity.Identifiable;
import us.lump.lib.Money;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Vector;


/**
 * The hierarchy of budget, account, categories.
 *
 * @author Troy Bowman
 * @version $Id: Hierarchy.java,v 1.12 2008/07/20 01:37:57 troy Exp $
 */
public class Hierarchy extends JTree {
  private static Hierarchy singleton;
  private final State state = State.getInstance();
  private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();

  public static Hierarchy getInstance() {
    if (singleton == null) singleton = new Hierarchy();
    return singleton;
  }

  private Hierarchy() {
    super();
    setModel(new DefaultTreeModel(rootNode, false));

    getSelectionModel().setSelectionMode(
        TreeSelectionModel.SINGLE_TREE_SELECTION);
    addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(final TreeSelectionEvent e) {
        final Object o = ((DefaultMutableTreeNode)e.getPath()
            .getLastPathComponent()).getUserObject();
        final TableQueryBar tqb = TableQueryBar.getInstance();

        if (tqb.getBeginDate().after(tqb.getEndDate())) {
          Date temp = tqb.getBeginDate();
          tqb.setBeginDate(tqb.getEndDate());
          tqb.setEndDate(temp);
        }

        if (o instanceof Account || o instanceof Category) {
          String type = o instanceof Account
                        ? Strings.get("account")
                        : Strings.get("category");
          final EnvelopeRunnable refresh = new EnvelopeRunnable(
              MessageFormat.format("{0} {1} {2}",
                                   Strings.get("retrieving"),
                                   o.toString(),
                                   type)) {

            public void run() {
              final TableModel tm = new TransactionTableModel(
                  (Identifiable)o, tqb.getBeginDate(), tqb.getEndDate());

              final StatusElement se = StatusBar.getInstance()
                  .addTask(Strings.get("preparing.table"));

              SwingUtilities.invokeLater(new Runnable() {
                public void run() {

                  final JTable table = new JTable(tm);
                  table.setDefaultRenderer(Money.class, new MoneyRenderer());
                  table.getTableHeader().setUpdateTableInRealTime(true);
                  table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
                  table.getTableHeader().setReorderingAllowed(false);
//              table.setPreferredSize(new Dimension(table.getParent().getWidth(),table.getHeight()));
                  table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                  tqb.setTitleLabel(
                      o instanceof Account
                      ? ((Account)o).getName()
                      : o instanceof Category
                        ? ((Category)o).getName() : null);
                  MainFrame.getInstance()
                      .setContentPane(tqb.getTableQueryPanel());
                  tqb.setViewportView(table);


                  initColumnSizes(
                      table,
                      ((TransactionTableModel)table.getModel()).getTransactions());
                  table.scrollRectToVisible(
                      table.getCellRect(tm.getRowCount() - 1, 0, true));

                  ((Component)e.getSource()).repaint();
                  RepaintManager.currentManager((Component)e.getSource())
                      .paintDirtyRegions();

                  StatusBar.getInstance().removeTask(se);
                }
              });
            }
          };

          for (ActionListener a : tqb.getRefreshButton().getActionListeners())
            tqb.getRefreshButton().removeActionListener(a);

          tqb.getRefreshButton().addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
              ThreadPool.getInstance().execute(refresh);
            }
          });

          ThreadPool.getInstance().execute(refresh);
        }
      }
    });
  }

  public void setRootNode(Budget budget) {
    rootNode.setUserObject(budget);

    EnvelopeRunnable r = new EnvelopeRunnable(Strings.get("building.tree")) {
      public void run() {
        for (Account a
            : CriteriaFactory.getInstance()
            .getAccountsForBudget(state.getBudget()))
          state.getAccounts().add(a);

        List<Category> categoryList =
            CriteriaFactory.getInstance()
                .getCategoriesForBudget(state.getBudget());

        for (Category c : categoryList) {
          state.getAccounts().add(c.getAccount());
        }

        for (Account a : state.getAccounts()) {
          DefaultMutableTreeNode thisNode = new DefaultMutableTreeNode(a);
          Collections.sort(a.getCategories());
          for (Category c : a.getCategories()) {
            thisNode.add(new DefaultMutableTreeNode(c));
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

  private void initColumnSizes(JTable table, Vector<Object[]> transactions) {
    TableModel model = table.getModel();
    TableColumn column;
    Component comp;
    int headerWidth = 0;
    int cellWidth;
    TableCellRenderer headerRenderer =
        table.getTableHeader().getDefaultRenderer();

    for (int i = 0; i < 7; i++) {
      column = table.getColumnModel().getColumn(i);

      comp = headerRenderer.getTableCellRendererComponent(
          null, column.getHeaderValue(),
          false, false, 0, 0);
      headerWidth = comp.getPreferredSize().width;

      cellWidth = 0;
      String value = null;
      if (i == 1) {
        cellWidth =
            comp.getFontMetrics(table.getFont()).stringWidth(" May 77, 7777");
      }
      if (i == 2 || i == 3 || i == 4) {
        cellWidth =
            comp.getFontMetrics(Fonts.getFont("fixed"))
                .stringWidth("$9,999,999.00");
      }

      for (int x = 0; x < transactions.size(); x++) {
        comp = table.getDefaultRenderer(model.getColumnClass(i)).
            getTableCellRendererComponent(
                table,
                transactions.get(x)[i],
                false, false, x, i);

        if (comp.getPreferredSize().width > cellWidth)
          cellWidth = comp.getPreferredSize().width;
      }

      if (i == 0) column.setMaxWidth(cellWidth);

      column.setPreferredWidth(Math.max(headerWidth, cellWidth));
//      column.setPreferredWidth(cellWidth);
      column.setWidth(cellWidth);
      column.setMinWidth(cellWidth);
      RepaintManager.currentManager(table).paintDirtyRegions();

    }

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
}
