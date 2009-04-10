package us.lump.envelope.client.portal;

import us.lump.envelope.command.Command;
import us.lump.envelope.exception.AbortException;

/**
 * Transaction Methods.
 *
 * @author troy
 * @version $Id: TransactionPortal.java,v 1.10 2009/04/10 22:49:27 troy Exp $
 */
@SuppressWarnings({"unchecked"})
public class TransactionPortal extends Portal {

  public void updateReconciled(Integer transactionId, Boolean reconciled)
      throws AbortException {
    invoke(new Command(Command.Name.updateReconciled, null, transactionId, reconciled));
  }
}
