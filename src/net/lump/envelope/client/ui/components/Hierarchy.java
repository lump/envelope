package us.lump.envelope.client.ui.components;

import us.lump.envelope.client.CriteriaFactory;
import us.lump.envelope.client.State;
import us.lump.envelope.client.thread.StatusRunnable;
import us.lump.envelope.client.thread.ThreadPool;
import us.lump.envelope.client.ui.components.forms.TableQueryBar;
import us.lump.envelope.client.ui.components.models.MoneyRenderer;
import us.lump.envelope.client.ui.components.models.TransactionTableModel;
import us.lump.envelope.client.ui.defs.Colors;
import us.lump.envelope.client.ui.defs.Fonts;
import us.lump.envelope.client.ui.defs.Strings;
import static us.lump.envelope.client.ui.images.ImageResource.icon.*;
import us.lump.envelope.shared.entity.Account;
import us.lump.envelope.shared.entity.Budget;
import us.lump.envelope.shared.entity.Category;
import us.lump.envelope.shared.exception.AbortException;
import us.lump.lib.Money;

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
import java.util.Date;
import java.util.HashMap;
import java.util.List;


/**
 * The hierarchy of budget, account, categories.
 *
 * @author Troy Bowman
 * @version $Id: Hierarchy.java,v 1.32 2009/07/13 17:21:44 troy Exp $
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
          if (tm == null) {
            tm = new TransactionTableModel(
                o, tqb.getBeginDate(), tqb.getEndDate(),
                tqb.getTable());
          } else tm.queue(o, tqb.getBeginDate(), tqb.getEndDate());
          if (!table.getModel().equals(tm)) table.setModel(tm);

          table.setDefaultRenderer(Money.class, new MoneyRenderer());
          tqb.setTitleLabel(o.toString());
          tqb.setTitleIcon(getIconForObject(o, true));

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
        if (children == null || children.size() == 0) return;

        // nuke any number of children that in indexes creater than new list
        for (int x = children.size(); x < node.getChildCount(); x++)
          node.remove(x);

        for (int x = 0; x < children.size(); x++) {
          DefaultMutableTreeNode dmtn = null;
          if (x < node.getChildCount()) {
            dmtn = ((DefaultMutableTreeNode)node.getChildAt(x));
            dmtn.setUserObject(children.get(x));
            final DefaultMutableTreeNode fdmtn = dmtn;
            SwingUtilities.invokeLater(new Runnable() {
              public void run() { treeModel.nodeChanged(fdmtn); }
            });
          }
          if (x >= node.getChildCount()) {
            dmtn = new DefaultMutableTreeNode(children.get(x));
            node.add(dmtn);
            final int[] fx = new int[]{x};
            SwingUtilities.invokeLater(new Runnable() {
              public void run() { treeModel.nodesWereInserted(node, fx); }
            });
          }
          if (dmtn != null) {
            if (selectedObject != null
                && dmtn.getUserObject().equals(
                ((DefaultMutableTreeNode)selectedObject).getUserObject()))
              singleton.setSelectionPath(new TreePath(dmtn.getPath()));

            updateChildren(dmtn);
          }
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
            treeModel.nodeChanged(rootNode);
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
      } else if (isOpaque()) {
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
      } else {
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
        } else if (o instanceof CategoryTotal) {
          if (o instanceof AccountTotal) {
            if (expanded) mainLabel.setIcon(account.get());
            else mainLabel.setIcon(account_closed.get());
            mainLabel.setText(((AccountTotal)o).name);
            balanceLabel.setText("");
          } else {
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
          balanceLabel.setText(((CategoryTotal)o).balance.toFormattedString());
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
            } else {
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
            } else {
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
