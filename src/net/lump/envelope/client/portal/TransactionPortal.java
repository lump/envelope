package us.lump.envelope.client.portal;

import us.lump.envelope.shared.command.Command;
import us.lump.envelope.shared.exception.AbortException;

/**
 * Transaction Methods.
 *
 * @author troy
 * @version $Id: TransactionPortal.java,v 1.11 2009/07/13 17:21:44 troy Exp $
 */
@SuppressWarnings({"unchecked"})
public class TransactionPortal extends Portal {

  public void updateReconciled(Integer transactionId, Boolean reconciled)
      throws AbortException {
    invoke(new Command(Command.Name.updateReconciled, null, transactionId, reconciled));
  }
}
