package us.lump.envelope.client.ui;

import us.lump.envelope.entity.Category;
import us.lump.envelope.entity.Transaction;
import us.lump.envelope.entity.Account;
import us.lump.envelope.client.CriteriaFactory;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/**
 * A table model which lists transactions.
 * @author Troy Bowman
 * @version $Id: TransactionTableModel.java,v 1.1 2008/07/06 07:22:06 troy Exp $
 */
public class TransactionTableModel extends AbstractTableModel {
  private List<Transaction> transactions;


  TransactionTableModel(Account account) {
    transactions =
        CriteriaFactory.getInstance().getTransactionsForAccount(account);
  }

  public int getRowCount() {
    return transactions.size();
  }

  public int getColumnCount() {
    return 8;
  }

  public Object getValueAt(int rowIndex, int columnIndex) {
    Transaction t = transactions.get(rowIndex);

    switch (columnIndex) {
        case 0: return t.getId();
        case 1: return t.getReconciled();
        case 2: return t.getDate();
        case 3: return t.getAllocations();
        case 4: return t.getEntity();
        case 5: return t.getDescription();
        case 6: return t.getStamp();
        case 7: return t.getTransfer();
    }
    return null;
  }

  public String getColumnName(int columnIndex) {
    switch (columnIndex) {
        case 0: return "Id";
        case 1: return "Reconciled";
        case 2: return "Date";
        case 3: return "Allocations";
        case 4: return "Entity";
        case 5: return "Description";
        case 6: return "Stamp";
        case 7: return "Transfer"; 
    }
    return null;

  }
}
