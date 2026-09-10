package net.lump.envelope.server.dao;

import net.lump.envelope.shared.entity.Allocation;
import net.lump.envelope.shared.entity.Category;
import net.lump.envelope.shared.entity.Transaction;
import net.lump.envelope.shared.exception.EnvelopeException;
import net.lump.lib.Money;
import org.hibernate.Hibernate;
import org.hibernate.LockMode;

import java.sql.Date;
import java.util.List;

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
    Integer transactionId = allocation.getTransaction().getId();

    // Count the siblings with a LOCKING read, which does two things that a plain
    // one does not.  It serializes concurrent deletes against the same
    // transaction, and it reads the latest committed rows: under REPEATABLE READ
    // an ordinary SELECT would answer from the snapshot taken before we waited,
    // so a second caller would still see the row the first one just removed.
    // Without it this is check-then-act -- two deletes on a two-allocation
    // transaction each see two, each pass, and the transaction is left with none.
    // Deleting a child never touches the parent's @Version, so optimistic locking
    // offers nothing here.
    @SuppressWarnings("unchecked")
    List<Allocation> siblings = getCurrentSession()
        .createQuery("from Allocation a where a.transaction.id = :transactionId")
        .setParameter("transactionId", transactionId)
        .setLockMode("a", LockMode.PESSIMISTIC_WRITE)
        .list();

    // A transaction reaches its budget only through its allocations -- the
    // transactions table carries no budget column -- so a transaction with none
    // is invisible to every query the client can make, and its row is stranded.
    // Removing the last allocation means deleting the transaction, which is a
    // different operation.
    if (siblings.size() <= 1)
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data,
          "a transaction must keep at least one allocation");

    delete(allocation);
  }

  /**
   * Create a transaction together with its first allocation.
   *
   * <p>The two are made in one go on purpose.  A transaction carries no budget
   * column and is reachable only through its allocations, so one saved on its own
   * -- even for the moment between two round trips -- is a row no query can find.
   * Controller commits this method as a single database transaction, so either
   * both rows exist or neither does.
   *
   * @param categoryId  the category the first allocation belongs to
   * @param date        the transaction date
   * @param entity      who it was paid to or received from
   * @param description free text
   * @param amount      the first allocation's amount
   *
   * @return the new Transaction, detached, with its allocation loaded
   */
  public Transaction createTransaction(Integer categoryId, Date date, String entity,
                                       String description, Money amount)
      throws EnvelopeException {
    if (categoryId == null)
      throw new EnvelopeException(
          EnvelopeException.Name.Invalid_Data, "a transaction needs a category");

    Category category = load(Category.class, categoryId);

    Transaction transaction = new Transaction();
    transaction.setDate(date);
    transaction.setEntity(entity == null ? "" : entity);
    transaction.setDescription(description == null ? "" : description);
    transaction.setReconciled(false);
    transaction.setTransfer(false);

    Allocation allocation = new Allocation();
    allocation.setTransaction(transaction);
    allocation.setCategory(category);
    allocation.setAmount(amount == null ? Money.ZERO : amount);

    getCurrentSession().save(transaction);
    getCurrentSession().save(allocation);
    getCurrentSession().flush();

    // re-read so the mapped allocations collection reflects what was just written,
    // then detach it the way Generic.get does before it goes over the wire
    getCurrentSession().refresh(transaction);
    Hibernate.initialize(transaction);
    evict(transaction);
    return transaction;
  }

  /**
   * Delete a transaction and every allocation on it.
   *
   * <p>The allocations go first and in the same database transaction: their
   * foreign key is ON DELETE RESTRICT and the inverse side carries no cascade, so
   * deleting the parent while children remain simply fails.  The children are
   * taken with a locking read so a concurrent deleteAllocation cannot interleave.
   *
   * @param transactionId the transaction to remove
   */
  public void deleteTransaction(Integer transactionId) throws EnvelopeException {
    Transaction transaction = load(Transaction.class, transactionId);

    @SuppressWarnings("unchecked")
    List<Allocation> allocations = getCurrentSession()
        .createQuery("from Allocation a where a.transaction.id = :transactionId")
        .setParameter("transactionId", transactionId)
        .setLockMode("a", LockMode.PESSIMISTIC_WRITE)
        .list();

    for (Allocation allocation : allocations) delete(allocation);
    getCurrentSession().flush();

    delete(transaction);
  }
}
