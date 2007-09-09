package us.lump.envelope.server.dao;

import org.apache.log4j.Logger;
import org.hibernate.*;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Order;
import org.hibernate.impl.SessionImpl;
import us.lump.envelope.entity.*;
import us.lump.envelope.entity.Transaction;
import us.lump.envelope.server.PrefsConfigurator;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * DataDispatch through DAO.
 *
 * @author Troy Bowman
 * @version $Id: DAO.java,v 1.5 2007/09/09 07:17:10 troy Exp $
 */
public abstract class DAO {
  final Logger logger;

  private static SessionFactory sessionFactory = null;

  {
    logger = Logger.getLogger(this.getClass());

    if (!this.isActive()) begin();
  }

  /** Initialize the session factory in the DAO. */
  public static void initialize() {
    if (sessionFactory == null) {
      Properties config = PrefsConfigurator.configure(DAO.class);
      sessionFactory = new AnnotationConfiguration()
//          .addPackage("us.lump.envelope.entity")
          .addAnnotatedClass(Account.class)
          .addAnnotatedClass(Budget.class)
          .addAnnotatedClass(Category.class)
          .addAnnotatedClass(Income.class)
          .addAnnotatedClass(Transaction.class)
          .addAnnotatedClass(Allocation.class)
          .addAnnotatedClass(Tag.class)
          .addAnnotatedClass(User.class)
          .addProperties(config).buildSessionFactory();
    }
  }

  static SessionFactory getSessionFactory() {
    initialize();
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


  protected <T extends Identifiable> void delete(T... is) {
    this.delete(listify(is));
  }

  protected <T extends Identifiable> void delete(Iterable<T> l) {
    for (T i : l) getCurrentSession().delete(i);
  }

  protected <T extends Identifiable> void evict(T... is) {
    this.evict(listify(is));
  }

  protected <T extends Identifiable> void evict(Iterable<T> l) {
    for (T i : l) getCurrentSession().evict(i);
  }

  @SuppressWarnings("unchecked")
  protected <T extends Identifiable> T get(
      Class<T> t,
      Serializable id) {
    return (T)getCurrentSession().get(t, id);
  }

  protected <T extends Identifiable> List<T> getList(
      Class<T> t,
      Serializable... ids) {
    return getList(t, listify(ids));
  }

  protected <T extends Identifiable> List<T> getList(
      Class<T> t,
      Iterable<Serializable> ids) {
    List<T> out = new ArrayList<T>();
    for (Serializable id : ids) out.add(get(t, id));
    return out;
  }

  @SuppressWarnings("unchecked")
  protected <T extends Identifiable> List<T> list(
      Class<T> t,
      List<Order> orderby,
      Criterion... crits) {
    Criteria criteria = getCurrentSession().createCriteria(t);
    for (Criterion crit : crits) criteria.add(crit);
    if (orderby != null) for (Order o : orderby) criteria.addOrder(o);
    criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
    return (List<T>)criteria.list();
  }

  protected <T extends Identifiable> List<T> list(
      Class<T> t,
      Criterion... crits) {
    return list(t, null, crits);
  }

  @SuppressWarnings("unchecked")
  protected <T extends Identifiable> T load(
      Class<T> t,
      Serializable id) {
    return (T)getCurrentSession().load(t, id);
  }

  protected <T extends Identifiable> List<T> loadList(
      Class<T> t,
      Serializable... ids) {
    return loadList(t, listify(ids));
  }

  protected <T extends Identifiable> List<T> loadList(
      Class<T> t,
      Iterable<Serializable> ids) {
    List<T> out = new ArrayList<T>();
    for (Serializable id : ids) out.add(load(t, id));
    return out;
  }

  @SuppressWarnings("unchecked")
  protected <T extends Identifiable> T merge(T i) {
    return (T)getCurrentSession().merge(i);
  }

  protected <T extends Identifiable> List<T> mergeList(T... is) {
    return mergeList(listify(is));
  }

  protected <T extends Identifiable> List<T> mergeList(Iterable<T> is) {
    List<T> out = new ArrayList<T>();
    for (T i : is) out.add(merge(i));
    return out;
  }

  protected <T extends Identifiable> void refresh(T... is) {
    refresh(listify(is));
  }

  protected <T extends Identifiable> void refresh(Iterable<T> is) {
    for (T i : is) getCurrentSession().refresh(i);
  }

  protected <T extends Identifiable> Serializable save(T os) {
    return getCurrentSession().save(os);
  }

  protected <T extends Identifiable> Serializable saveOrUpdate(T o) {
    if (o.getId() == null)
      return getCurrentSession().save(o);
    else {
      getCurrentSession().update(o);
      return o.getId();
    }
  }

  protected <T extends Identifiable> List<Serializable> saveOrUpdateList(
      T... os) {
    return saveOrUpdateList(listify(os));
  }

  protected <T extends Identifiable> List<Serializable> saveOrUpdateList(
      Iterable<T> os) {
    List<Serializable> out = new ArrayList<Serializable>();
    for (T o : os) out.add(saveOrUpdate(o));
    return out;
  }

  protected <T extends Identifiable> void update(T... os) {
    update(listify(os));
  }

  protected <T extends Identifiable> void update(Iterable<T> os) {
    for (T o : os) getCurrentSession().update(o);
  }

  /** Begin a new transaction. */
  void begin() {
    getTransaction().begin();
    logger.debug("began transaction");
  }

  /**
   * Flush the associated Session and end the unit of work (unless we are in
   * FlushMode.NEVER. This method will commit the underlying transaction if and
   * only if the underlying transaction was initiated by this object.
   *
   * @throws org.hibernate.HibernateException
   *          - Indicates problems flushing the session or talking to the
   *          database.
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
   * Force this session to flush. Must be called at the end of a unit of work,
   * before commiting the transaction and closing the session (depending on
   * flush-mode, Transaction.commit() calls this method). Flushing is the
   * process of synchronizing the underlying persistent store with persistable
   * state held in memory.
   *
   * @throws org.hibernate.HibernateException
   *          - Indicates problems flushing the session or talking to the
   *          database.
   */
  public void flush() throws HibernateException {
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
   * Does this session contain any changes which must be synchronized with the
   * database? In other words, would any DML operations be executed if we
   * flushed this session?
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
   * Check if this transaction was successfully committed. This method could
   * return false even after successful invocation of commit. As an example, JTA
   * based strategies no-op on commit calls if they did not start the
   * transaction; in that case, they also report wasCommitted as false.
   *
   * @return boolean True if the transaction was (unequivocally) committed via
   *         this local transaction; false otherwise.
   */
  public boolean wasCommitted() {
    return getTransaction().wasCommitted();
  }

  /**
   * Was this transaction rolled back or set to rollback only? This only
   * accounts for actions initiated from this local transaction. If, for
   * example, the underlying transaction is forced to rollback via some other
   * means, this method still reports false because the rollback was not
   * initiated from here.
   *
   * @return boolean True if the transaction was rolled back via this local
   *         transaction; false otherwise.
   */
  public boolean wasRolledBack() {
    return getTransaction().wasRolledBack();
  }

  /**
   * End the session by releasing the JDBC connection and cleaning up. It is not
   * strictly necessary to close the session but you must at least disconnect()
   * it.
   *
   * @return the connection provided by the application or null.
   */
  public Connection close() {
    logger.debug("closing session");
    return getCurrentSession().close();
  }

  /**
   * Disconnect the Session from the current JDBC connection. If the connection
   * was obtained by Hibernate close it and return it to the connection pool;
   * otherwise, return it to the application. This is used by applications which
   * supply JDBC connections to Hibernate and which require long-sessions (or
   * long-conversations) Note that disconnect() called on a session where the
   * connection was retrieved by Hibernate through its configured
   * org.hibernate.connection.ConnectionProvider has no effect, provided
   * ConnectionReleaseMode.ON_CLOSE is not in effect.
   *
   * @return the application-supplied connection or null
   */
  public Connection disconnect() {
    logger.debug("disconnecting from jdbc pool");
    return getCurrentSession().disconnect();
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
  Connection getConnection() throws SQLException {
    Connection c =
        ((SessionImpl)getCurrentSession()).getJDBCContext().borrowConnection();
    if (c.isClosed()) throw new SessionException("Session is closed!");
    return c;
  }


  <T> Iterable<T> listify(T... is) {
    ArrayList<T> list = new ArrayList<T>();
    Collections.addAll(list, is);
    return list;
  }

  protected void finalize() throws Throwable {
    try {
      if (isActive() && !wasRolledBack() && isDirty()) {
        commit();
      }
      if (!getConnection().isClosed()) {
        close();
        disconnect();
      }
    } finally {
      super.finalize();
    }
  }
}

