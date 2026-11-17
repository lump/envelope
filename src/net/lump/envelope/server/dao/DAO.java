package net.lump.envelope.server.dao;

import net.lump.envelope.server.ThreadInfo;
import net.lump.envelope.shared.entity.*;
import net.lump.envelope.shared.exception.EnvelopeException;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.resource.transaction.spi.TransactionStatus;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * DataDispatch through DAO.
 *
 * @author Troy Bowman
 */
public abstract class DAO {
  static final Logger logger = Logger.getLogger(DAO.class.getName());

  private static SessionFactory sessionFactory = null;
  private static ServiceRegistry serviceRegistry = null;

  private static final int cacheSize = 32;
  private static final int cacheTtl = 4;
  private static final int cacheTti = 5;

  {
    if (!this.isActive()) begin();
  }

  /** Initialize the session factory in the DAO.
   * @param config
   * @param config
   * @throws java.io.IOException*/
  /**
   * Initialize the session factory in the DAO.
   *
   * @param config a properties that configures hibernate
   */
  /** why the last initialize() failed, so a request can say so instead of NPE-ing */
  private static volatile String initializationFailure = null;

  public static void initialize(Properties config) {
    if (sessionFactory == null) {
      try {
        // The ehcache 2 region factory that used to be forced in here no longer
        // exists; the second-level cache is configured (off) in DAO.properties now.
      Configuration c = new Configuration()
          .addAnnotatedClass(Account.class)
          .addAnnotatedClass(Budget.class)
          .addAnnotatedClass(Category.class)
          .addAnnotatedClass(AllocationPreset.class)
//          .addAnnotatedClass(AllocationSetting.class)
//          .addAnnotatedClass(CategoryAllocationSetting.class)
          .addAnnotatedClass(Transaction.class)
          .addAnnotatedClass(Allocation.class)
          .addAnnotatedClass(User.class)
          .addProperties(config);
      SessionFactory built = c.buildSessionFactory();
      // The made-up verifiers unknown users are answered with derive from a
      // secret that must be stable and secret; see Logins.setSecretFrom.
      Logins.setSecretFrom(config.getProperty("hibernate.connection.password"),
                           config.getProperty("hibernate.connection.url"));
      // The users table must hold SCRAM verifiers and no md5-crypt hashes before
      // a login is taken; if not, the server stays not ready saying what to do.
      try {
        SchemaCheck.run(built);
      } catch (RuntimeException e) {
        built.close();
        throw e;
      }
      sessionFactory = built;
      initializationFailure = null;
      logger.info("session factory built against " + config.getProperty("hibernate.connection.url"));
      } catch (Exception e) {
        // Keep the reason.  Swallowing it here left sessionFactory null, and
        // every request afterwards died with a NullPointerException deep in
        // getCurrentSession() -- a message with nothing in it, while the real
        // one (usually: cannot reach the database) sat in the startup log.
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        // a migration to apply is said in our own words; anything else by its root cause
        initializationFailure = (e instanceof IllegalStateException && e.getMessage() != null
                                 && e.getMessage().contains("sql/migrations/"))
            ? e.getMessage()
            : root.getClass().getSimpleName() + ": " + root.getMessage()
              + " (url " + config.getProperty("hibernate.connection.url") + ")";
        logger.fatal("could not build the session factory -- " + initializationFailure, e);
      }

    }
  }

  static SessionFactory getSessionFactory() {
    if (sessionFactory == null)
      throw new IllegalStateException(initializationFailure != null
          ? "the database is not available: " + initializationFailure
          : "the session factory was never initialized");
    return sessionFactory;
  }

  /**
   * Get the current entity session.
   *
   * @return Session
   */
  public Session getCurrentSession() {
    // http://www.entity.org/42.html
    // A Session is opened when getCurrentSession() is called for the first time
    // and closed when the transaction ends. It is also flushed automatically
    // before the transaction commits. You can call getCurrentSession() as often
    // and anywhere you want as long as the transaction runs.
    return getSessionFactory().getCurrentSession();
  }

  // The session verbs the DAOs use, in Jakarta Persistence terms.  Hibernate 7
  // removed save/saveOrUpdate/update/delete/evict/load from the Session; the
  // generic helpers that wrapped them -- list by Criterion, get/load/merge/save
  // of lists, saveOrUpdate of any Identifiable, identifier and version access by
  // ClassMetadata -- had no callers left after 0.12 and went with them.

  /** Remove a managed entity. */
  public <T extends Identifiable> void delete(T i) {
    getCurrentSession().remove(i);
  }

  /**
   * Hand an entity back to the client: flush, then detach EVERYTHING the session
   * holds, not just this entity.  Hibernate 7 cascades PERSIST at flush time, as
   * Jakarta Persistence says to, where 5 under native bootstrap did not -- so
   * detaching a new Transaction while its new Allocation stayed managed, with a
   * PERSIST cascade pointing up at it, failed the commit with "detached entity
   * passed to persist", after the reply had already said it worked.  Only call
   * this as a command's last act: nothing is managed afterwards.
   */
  protected <T extends Identifiable> T evict(T i) {
    getCurrentSession().flush();
    getCurrentSession().clear();
    return i;
  }

  /** The row, loaded, or null if there is none. */
  public <T extends Identifiable> T get(Class<T> t, Serializable id) {
    return getCurrentSession().find(t, id);
  }

  /**
   * A reference to a row that is known to exist, loaded lazily -- what Session.load
   * was.  Only for use inside a command; a reference never initialized must not be
   * returned to the client.
   */
  public <T extends Identifiable> T load(Class<T> t, Serializable id) {
    return getCurrentSession().getReference(t, id);
  }

  public User getUser(String username) throws EnvelopeException {

    User user = getCurrentSession()
        .createSelectionQuery("from User u where u.name = :name", User.class)
        .setParameter("name", username)
        .uniqueResult();
    if (user == null)
      throw new EnvelopeException(EnvelopeException.Name.Invalid_User,
          "User " + username + " is invalid.");

    ThreadInfo.setUser(user);
    return user;
  }

  User getUser() throws IllegalStateException {
    User user = ThreadInfo.getUser();
    if (user == null)
      throw new IllegalStateException("user hasn't been set for this thread");
    return user;
  }

  // ------------------------------------------------------------------------
  // Whose budget.
  //
  // Permission says what a caller may do; these say to which rows.  A user has
  // exactly one budget, and every data command acts only inside it.  Each check
  // asks the database a scalar question -- the id of the budget a row belongs
  // to -- rather than loading the row, because a command that goes on to
  // saveOrUpdate a client's copy of that same row would otherwise collide with
  // the loaded one (NonUniqueObjectException).  A row that is missing and a row
  // in someone else's budget get the same answer, so the refusal says nothing
  // about what exists elsewhere.  Admins get no exemption here: ADMIN is about
  // users and budgets, and an admin's own money lives in their own budget like
  // anyone's.
  // ------------------------------------------------------------------------

  /** The id of the caller's budget. */
  protected Integer myBudgetId() throws EnvelopeException {
    Budget budget = getUser().getBudget();
    if (budget == null || budget.getId() == null)
      throw new EnvelopeException(EnvelopeException.Name.Permission_Denied, "you have no budget");
    return budget.getId();
  }

  /** Refuse unless the budget is the caller's. */
  protected void requireMyBudget(Integer budgetId) throws EnvelopeException {
    if (budgetId == null || !budgetId.equals(myBudgetId()))
      throw notYours("budget " + budgetId);
  }

  /** Refuse unless the account is in the caller's budget. */
  protected void requireMyAccount(Integer accountId) throws EnvelopeException {
    requireOwned("select a.budget.id from Account a where a.id = :id", accountId, "account");
  }

  /** Refuse unless the category is in the caller's budget. */
  protected void requireMyCategory(Integer categoryId) throws EnvelopeException {
    requireOwned("select c.account.budget.id from Category c where c.id = :id", categoryId, "category");
  }

  /** Refuse unless the allocation is in the caller's budget. */
  protected void requireMyAllocation(Integer allocationId) throws EnvelopeException {
    requireOwned("select a.category.account.budget.id from Allocation a where a.id = :id",
        allocationId, "allocation");
  }

  /** Refuse unless the preset row is in the caller's budget. */
  protected void requireMyPresetRow(Integer presetRowId) throws EnvelopeException {
    requireOwned("select p.budget.id from AllocationPreset p where p.id = :id", presetRowId, "preset row");
  }

  /**
   * Refuse unless the transaction is in the caller's budget.  A transaction has
   * no budget column and reaches one only through its allocations, so every one
   * of them has to be the caller's: one stray allocation elsewhere would let a
   * caller reach a row of someone else's through a transaction of their own.  A
   * transaction with no allocations reaches no budget, so it is nobody's.
   */
  protected void requireMyTransaction(Integer transactionId) throws EnvelopeException {
    if (transactionId == null) throw notYours("transaction null");
    List<Integer> budgets = getCurrentSession()
        .createSelectionQuery("select distinct a.category.account.budget.id from Allocation a"
                              + " where a.transaction.id = :id", Integer.class)
        .setParameter("id", transactionId)
        .list();
    Integer mine = myBudgetId();
    if (budgets.isEmpty()) throw notYours("transaction " + transactionId);
    for (Object b : budgets)
      if (!mine.equals(b)) throw notYours("transaction " + transactionId);
  }

  private void requireOwned(String hql, Integer id, String what) throws EnvelopeException {
    if (id == null) throw notYours(what + " null");
    Integer budget = getCurrentSession().createSelectionQuery(hql, Integer.class)
        .setParameter("id", id).uniqueResult();
    if (budget == null || !budget.equals(myBudgetId())) throw notYours(what + " " + id);
  }

  private EnvelopeException notYours(String what) {
    logger.warn(getUser().getName() + " was refused " + what + ", which is not in their budget");
    return new EnvelopeException(EnvelopeException.Name.Permission_Denied,
        "there is no " + what + " in your budget");
  }


  /** Begin a new transaction. */
  void begin() {
    getTransaction().begin();
    logger.debug("began transaction");
  }

  /**
   * Flush the associated Session and end the unit of work (unless we are in FlushMode.NEVER. This method will commit the underlying
   * transaction if and only if the underlying transaction was initiated by this object.
   *
   * @throws org.hibernate.HibernateException
   *          - Indicates problems flushing the session or talking to the database.
   */
  public void commit() throws HibernateException {
    getTransaction().commit();
    logger.debug("committed transaction");
  }

  /** Force the underlying transaction to roll back. */
  public void rollback() {
    getTransaction().rollback();
    logger.debug("rolled back transaction");
  }

  /**
   * Force this session to flush. Must be called at the end of a unit of work, before commiting the transaction and closing the
   * session (depending on flush-mode, Transaction.commit() calls this method). Flushing is the process of synchronizing the
   * underlying persistent store with persistable state held in memory.
   *
   * @throws org.hibernate.HibernateException
   *          - Indicates problems flushing the session or talking to the database.
   */
  public synchronized void flush() throws HibernateException {
    getCurrentSession().flush();
    logger.debug("flushed session");
  }

  /**
   * Check if the session is still open.
   *
   * @return boolean
   */
  public boolean isOpen() {
    return getCurrentSession().isOpen();
  }

  /**
   * Does this session contain any changes which must be synchronized with the database? In other words, would any DML operations be
   * executed if we flushed this session?
   *
   * @return True if the session contains pending changes; false otherwise.
   */
  public boolean isDirty() {
    return getCurrentSession().isDirty();
  }

  /**
   * Check if the session is currently connected.
   *
   * @return boolean
   */
  public boolean isConnected() {
    return getCurrentSession().isConnected();
  }

  /**
   * Is this transaction still active?
   *
   * @return boolean
   */
  public boolean isActive() {
    return getTransaction().isActive();
  }

  /**
   * Check if this transaction was successfully committed. This method could return false even after successful invocation of
   * commit. As an example, JTA based strategies no-op on commit calls if they did not start the transaction; in that case, they
   * also report wasCommitted as false.
   *
   * @return boolean True if the transaction was (unequivocally) committed via this local transaction; false otherwise.
   */
  public boolean wasCommitted() {
    return getTransaction().getStatus() == TransactionStatus.COMMITTED;
  }

  /**
   * Was this transaction rolled back or set to rollback only? This only accounts for actions initiated from this local transaction.
   * If, for example, the underlying transaction is forced to rollback via some other means, this method still reports false because
   * the rollback was not initiated from here.
   *
   * @return boolean True if the transaction was rolled back via this local transaction; false otherwise.
   */
  public boolean wasRolledBack() {
    TransactionStatus status = getTransaction().getStatus();
    return status == TransactionStatus.ROLLED_BACK
           || status == TransactionStatus.MARKED_ROLLBACK
           || status == TransactionStatus.ROLLING_BACK;
  }

  /**
   * End the session by releasing the JDBC connection and cleaning up. It is not strictly necessary to close the session but you
   * must at least disconnect() it.
   *
   * <p>Returned the JDBC connection before Hibernate 5; now void.
   */
  public void close() {
    logger.debug("closing session");
    getCurrentSession().close();
  }

  /**
   * Get the current Transsaction.
   *
   * @return org.hibernate.Transaction
   */
  public org.hibernate.Transaction getTransaction() {
    return getCurrentSession().getTransaction();
  }

  /**
   * Get the JDBC Connection for the current session.
   *
   * @return Connection
   *
   * @throws SQLException if the session is closed.
   */
  /*
  Connection getConnection() throws SQLException {
    Connection c =
        ((SessionImpl)getCurrentSession()).getJDBCContext().borrowConnection();
    if (c.isClosed()) throw new SessionException("Session is closed!");
    return c;
  }
  */

  // finalize() used to commit and close here when a DAO was collected.  It ran on
  // the finalizer thread, whose getCurrentSession() opened a session of its own
  // rather than finding the request's, and Controller already commits and the
  // session context already closes every session at transaction end.  Removed
  // with Hibernate 7, which also removed the Session.disconnect() it called.
}

