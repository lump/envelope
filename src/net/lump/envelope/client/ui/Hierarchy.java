package us.lump.envelope.client.ui;

import us.lump.envelope.entity.Budget;
import us.lump.envelope.entity.Category;
import us.lump.envelope.entity.Account;
import us.lump.envelope.client.CriteriaFactory;
import us.lump.envelope.client.State;

import javax.swing.*;
import javax.swing.table.TableColumn;
import javax.swing.table.TableCellRenderer;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.*;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.awt.*;


/**
 * The hierarchy of budget, account, categories.
 *
 * @author Troy Bowman
 * @version $Id: Hierarchy.java,v 1.3 2008/07/08 06:41:25 troy Exp $
 */
public class Hierarchy extends JTree {
  private static Hierarchy singleton;
  private State state = State.getInstance();
  private DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();

  public static Hierarchy getInstance() {
    if (singleton == null) singleton = new Hierarchy();
    return singleton;
  }

  private Hierarchy() {
    super();
    setModel(new DefaultTreeModel(rootNode, false));

    getSelectionModel()
        .setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        System.err.println(e.toString());
        Object o = ((DefaultMutableTreeNode)e.getPath().getLastPathComponent()).getUserObject();

        if (o instanceof Account) {
          final JTable table = new JTable(new TransactionTableModel((Account)o));
          table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
          table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
          SwingUtilities.invokeLater(new Runnable(){
            public void run() {
              initColumnSizes(table);
            }
          });

          TableQueryBar tqb = TableQueryBar.getInstance();
          tqb.setTitleLabel(((Account)o).getName());
          MainFrame.getInstance().setContentPane(tqb.getTableQueryPanel());
          tqb.setViewportView(table);
        }
        if (o instanceof Category) {
          final JTable table = new JTable(new AllocationTableModel((Category)o));
          table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
          table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                    
          TableQueryBar tqb = TableQueryBar.getInstance();
          tqb.setTitleLabel(((Category)o).getName());
          MainFrame.getInstance().setContentPane(tqb.getTableQueryPanel());
          tqb.setViewportView(table);
        }
      }
    });
  }

  public void setRootNode(Budget budget) {
    rootNode.setUserObject(budget);

    Runnable r = new Runnable() {
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

        singleton.expandPath(new TreePath(rootNode));
        singleton.repaint();
        RepaintManager.currentManager(singleton).paintDirtyRegions();

      }
    };
    SwingUtilities.invokeLater(r);
  }

  private void initColumnSizes(JTable table) {
    TransactionTableModel model = (TransactionTableModel)table.getModel();
    TableColumn column = null;
    Component comp = null;
//    int headerWidth = 0;
    int cellWidth = 0;
    ArrayList<Object[]> transactions = model.getTransactions();
    TableCellRenderer headerRenderer =
        table.getTableHeader().getDefaultRenderer();

    for (int i = 0; i < 7; i++) {
      column = table.getColumnModel().getColumn(i);

//      comp = headerRenderer.getTableCellRendererComponent(
//          null, column.getHeaderValue(),
//          false, false, 0, 0);
//      headerWidth = comp.getPreferredSize().width;

      cellWidth = 0;
      for (int x = 0; x < transactions.size(); x++) {
        comp = table.getDefaultRenderer(model.getColumnClass(i)).
            getTableCellRendererComponent(
                table, transactions.get(x)[i],
                false, false, x, i);
        cellWidth = Math.max(comp.getPreferredSize().width, cellWidth);
      }

//      column.setPreferredWidth(Math.max(headerWidth+2, cellWidth+2));
      column.setPreferredWidth(cellWidth+1);
      column.setWidth(cellWidth+1);
      column.setMinWidth(cellWidth);
      RepaintManager.currentManager(table).paintDirtyRegions();

    }

  }


}
