package net.lump.envelope.server.dao;

import net.lump.envelope.shared.entity.Allocation;
import net.lump.envelope.shared.entity.Transaction;
import net.lump.envelope.shared.exception.EnvelopeException;

/**
 * A DAO for Transactions.
 *
 * @author Troy Bowman
 * @version $Id: Action.java,v 1.11 2009/10/02 22:06:23 troy Exp $
 */
public class Action extends DAO {

  /**
   * Sets reconciled
   * @param transactionID the ID of the transaction
   * @param reconciled whether this is reconciled or not
   */
  public void updateReconciled(Integer transactionID, Boolean reconciled) {
    Transaction t = load(Transaction.class, transactionID);
    t.setReconciled(reconciled);
  }

  /**
   * Delete one Allocation.
   *
   * <p>Deliberately narrow rather than a generic "delete this entity" command.
   * Nothing on the server checks whose budget a command touches, so a generic
   * delete verb would hand every authenticated caller the ability to remove any
   * row of any type; this one can only ever remove an allocation, and it enforces
   * the invariant below no matter what the client believes.
   *
   * @param allocationId the id of the Allocation to remove
   *
   * @throws EnvelopeException if this is the transaction's last allocation
   */
  public void deleteAllocation(Integer allocationId) throws EnvelopeException {
    Allocation allocation = load(Allocation.class, allocationId);

    // A transaction reaches its budget only through its allocations -- the
    // transactions table carries no budget column -- so a transaction with none
    // is invisible to every query the client can make, and its row is stranded.
    // Removing the last allocation means deleting the transaction, which is a
    // different operation.
    if (allocation.getTransaction().getAllocations().size() <= 1)
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data,
          "a transaction must keep at least one allocation");

    delete(allocation);
  }
}
