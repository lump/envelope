package us.lump.envelope.client.ui;

import us.lump.envelope.entity.Category;
import us.lump.envelope.entity.Transaction;
import us.lump.envelope.entity.Account;
import us.lump.envelope.client.CriteriaFactory;
import us.lump.lib.Money;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.event.TableModelListener;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.awt.*;

/**
 * A table model which lists transactions.
 * @author Troy Bowman
 * @version $Id: TransactionTableModel.java,v 1.3 2008/07/08 06:41:25 troy Exp $
 */
public class TransactionTableModel implements TableModel {
//  private List<Transaction> transactions;
  private ArrayList<Object[]> transactions;
  String[] columnNames = new String[]{"C","Date","Amount","Balance","Reconciled","Entity","Description"};

  TransactionTableModel(Account account) {
    transactions = new ArrayList<Object[]>();
    Money balance = new Money(0);
    Money reconciled = new Money(0);
    ArrayList<Object[]> incoming =
        CriteriaFactory.getInstance().getTransactionsForAccount(account);
    for (Object[] row : incoming) {
      balance = new Money(balance.add((Money)row[2]));
      if ((Boolean)row[0]) reconciled = new Money(reconciled.add((Money)row[2]));
      transactions.add(new Object[]{row[0], row[1], row[2], new Money(balance), new Money(reconciled), row[3], row[4], row[5]});
    }
    System.out.println(transactions.size());
  }

  public ArrayList<Object[]> getTransactions() {
    return transactions;
  }

  public int getRowCount() {
    return transactions.size();
  }

  public int getColumnCount() {
    return 7;
  }

  public Object getValueAt(int rowIndex, int columnIndex) {
    return transactions.get(rowIndex)[columnIndex];
  }

  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
  }

  public void addTableModelListener(TableModelListener l) {
  }

  public void removeTableModelListener(TableModelListener l) {
  }

  public String getColumnName(int columnIndex) {
    return columnNames[columnIndex];
  }

  public Class<?> getColumnClass(int columnIndex) {
    return transactions.get(0)[columnIndex].getClass();
  }

  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return false;
  }


}
