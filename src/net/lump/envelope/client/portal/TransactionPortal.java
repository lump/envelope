package us.lump.envelope.client.portal;

import us.lump.envelope.Command;
import us.lump.envelope.entity.Transaction;

import java.sql.Date;
import java.util.List;

/**
 * Transaction Methods.
 *
 * @author troy
 * @version $Id: TransactionPortal.java,v 1.2 2007/09/09 07:17:10 troy Exp $
 */
@SuppressWarnings({"unchecked"})
public class TransactionPortal extends Portal {

  public List<Transaction> listTransactions(int year) {

    Command cmd = new Command(Command.Name.listTransactionsInYear, year);
    return (List<Transaction>)invoke(cmd);
  }

  public List<Transaction> listTransactions(Date date1, Date date2) {
    Command cmd = new Command(
        Command.Name.listTransactionsBetweenDates, date1, date2);
    return (List<Transaction>)invoke(cmd);
  }
}
