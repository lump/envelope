package us.lump.envelope.server.dao;

import us.lump.envelope.shared.entity.Transaction;

/**
 * A DAO for Transactions.
 *
 * @author Troy Bowman
 * @version $Id: Action.java,v 1.10 2009/07/13 17:21:44 troy Exp $
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
}
