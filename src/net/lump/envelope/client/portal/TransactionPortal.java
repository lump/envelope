package us.lump.envelope.client.portal;

import us.lump.envelope.Command;
import us.lump.envelope.exception.AbortException;

/**
 * Transaction Methods.
 *
 * @author troy
 * @version $Id: TransactionPortal.java,v 1.9 2009/02/01 02:33:42 troy Alpha $
 */
@SuppressWarnings({"unchecked"})
public class TransactionPortal extends Portal {

  public void updateReconciled(Integer transactionId, Boolean reconciled)
      throws AbortException {
    invoke(new Command(
        Command.Name.updateReconciled, transactionId, reconciled));
  }
}
