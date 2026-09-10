package net.lump.envelope.client.portal;

import net.lump.envelope.shared.command.Command;
import net.lump.envelope.shared.entity.Transaction;
import net.lump.envelope.shared.exception.AbortException;
import net.lump.lib.Money;

import java.sql.Date;

/**
 * Transaction Methods.
 *
 * @author troy
 * @version $Id: TransactionPortal.java,v 1.12 2009/10/02 22:06:23 troy Exp $
 */
@SuppressWarnings({"unchecked"})
public class TransactionPortal extends Portal {

  public void updateReconciled(Integer transactionId, Boolean reconciled)
      throws AbortException {
    invoke(new Command(Command.Name.updateReconciled, null, transactionId, reconciled));
  }

  /**
   * Remove one Allocation.  The server refuses to remove a transaction's last
   * allocation, so this can fail for a reason the caller should surface.
   *
   * @param allocationId the id of the Allocation to remove
   */
  public void deleteAllocation(Integer allocationId) throws AbortException {
    invoke(new Command(Command.Name.deleteAllocation, null, allocationId));
  }

  /**
   * Create a transaction and its first allocation in one server-side transaction.
   * A transaction is reachable only through its allocations, so it is never made
   * on its own.
   *
   * @return the new Transaction, with its allocation already loaded
   */
  public Transaction createTransaction(Integer categoryId, Date date, String entity,
                                       String description, Money amount)
      throws AbortException {
    return (Transaction)invoke(new Command(
        Command.Name.createTransaction, null, categoryId, date, entity, description, amount));
  }

  /**
   * Delete a transaction and every allocation on it.
   *
   * @param transactionId the transaction to remove
   */
  public void deleteTransaction(Integer transactionId) throws AbortException {
    invoke(new Command(Command.Name.deleteTransaction, null, transactionId));
  }

  /**
   * Lay a named allocation preset over a transaction, sized against a gross.
   *
   * <p>Done in one call rather than one save per row: a preset expands to dozens
   * of allocations, and they should either all land or none of them should.
   *
   * @return the transaction, with its allocations as they now stand
   */
  public Transaction applyAllocationPreset(Integer transactionId, String presetName, Money gross)
      throws AbortException {
    return (Transaction)invoke(new Command(
        Command.Name.applyAllocationPreset, null, transactionId, presetName, gross));
  }
}
