package us.lump.envelope.client.portal;

import us.lump.envelope.Command;
import us.lump.envelope.exception.EnvelopeException;
import us.lump.envelope.entity.Account;
import us.lump.envelope.entity.Category;
import us.lump.envelope.entity.Transaction;
import us.lump.lib.Money;

import java.sql.Date;
import java.util.List;

/**
 * Transaction Methods.
 *
 * @author troy
 * @version $Id: TransactionPortal.java,v 1.8 2008/09/13 19:17:42 troy Exp $
 */
@SuppressWarnings({"unchecked"})
public class TransactionPortal extends Portal {

  public void updateReconciled(Integer transactionId, Boolean reconciled)
      throws EnvelopeException {
    invoke(new Command(
        Command.Name.updateReconciled, transactionId, reconciled));
  }
}
