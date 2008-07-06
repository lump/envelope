package us.lump.envelope.client.ui;

import us.lump.envelope.entity.Budget;
import us.lump.envelope.entity.Category;
import us.lump.envelope.entity.Account;
import us.lump.envelope.client.CriteriaFactory;
import us.lump.envelope.client.State;

import javax.swing.*;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.*;
import java.util.List;
import java.util.Collections;


/**
 * The hierarchy of budget, account, categories.
 *
 * @author Troy Bowman
 * @version $Id: Hierarchy.java,v 1.2 2008/07/06 07:22:06 troy Exp $
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
          JScrollPane s = MainFrame.getInstance().getTableScrollPane();
          s.setViewportView(new JTable(new TransactionTableModel((Account)o)));
        }
        if (o instanceof Category) {
          JScrollPane s = MainFrame.getInstance().getTableScrollPane();
          s.setViewportView(new JTable(new AllocationTableModel((Category)o)));
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
}
