package us.lump.envelope.client.portal;

import us.lump.envelope.Command;
import us.lump.envelope.entity.Transaction;

import java.util.List;

/**
 * Transaction Methods.
 *
 * @author troy
 * @version $Id: TransactionPortal.java,v 1.1 2007/08/26 06:28:57 troy Exp $
 */
public class TransactionPortal extends Portal {

  @SuppressWarnings({"unchecked"})
  public List<Transaction> listTransactions(int year) {

    Command cmd = new Command(Command.Name.listTransactions)
        .set(Command.Param.year, year);

    return (List<Transaction>)invoke(cmd);
  }
}
