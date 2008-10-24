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
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.Date;
import java.util.HashMap;


/**
 * The hierarchy of budget, account, categories.
 *
 * @author Troy Bowman
 * @version $Id: Hierarchy.java,v 1.22 2008/10/24 19:23:07 troy Exp $
 */
public class Hierarchy extends JTree {
  private static Hierarchy singleton;
  private final State state = State.getInstance();
  private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
  TransactionTableModel tm;
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

    setModel(new DefaultTreeModel(rootNode, false));
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
        for (AccountTotal l : CriteriaFactory.getInstance()
            .getAccountTotals(state.getBudget())) {
          DefaultMutableTreeNode thisNode = new DefaultMutableTreeNode(l);
          for (CategoryTotal ca :
              CriteriaFactory.getInstance().getCategoriesForAccount(l.account)) {
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
        this.setBackground(backgroundNonSelectionColor);
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

    public AccountTotal(Account account, String name, Integer id, Money balance) {
      super(name, id, balance);
      this.account = account;
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

    protected JScrollPane getScrollPane() {
      Component c = tree.getParent();

      while (c != null && !(c instanceof JScrollPane))
        c = c.getParent();
      if (c instanceof JScrollPane)
        return (JScrollPane)c;
      return null;
    }

//    public void paint(Graphics g, JComponent c) {
//      configureLayoutCache();
//      if (c instanceof Hierarchy && c.getParent().getParent() instanceof JScrollPane) {
//        System.out.println("hello");
//      }
//      super.paint(g, c);
//    }

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
              size.height = prefSize.height + 2;
            } else {
              size = new Rectangle(
                  size.width = getRowX(row, depth), 0,
                  targetWidth, prefSize.height + 2);
            }

            return size;
          }
          return null;

        }
      };
    }
  }

}
