package us.lump.envelope.server.dao;

import us.lump.envelope.entity.Transaction;

/**
 * A DAO for Transactions.
 *
 * @author Troy Bowman
 * @version $Id: Action.java,v 1.9 2009/04/24 23:47:26 troy Exp $
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
