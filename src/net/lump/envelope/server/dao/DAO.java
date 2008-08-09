package us.lump.envelope.server.dao;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.apache.log4j.Logger;
import org.hibernate.*;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.hibernate.impl.SessionImpl;
import us.lump.envelope.entity.*;
import us.lump.envelope.entity.Transaction;
import us.lump.envelope.exception.DataException;
import us.lump.envelope.exception.EnvelopeException;
import us.lump.envelope.server.PrefsConfigurator;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * DataDispatch through DAO.
 *
 * @author Troy Bowman
 * @version $Id: DAO.java,v 1.18 2008/08/09 03:31:02 troy Exp $
 */
public abstract class DAO {
  final Logger logger;

  private static SessionFactory sessionFactory = null;

  private static final int cacheSize = 32;
  private static final int cacheTtl = 4;
  private static final int cacheTti = 5;

  // a userCache to ask the database for a User less.
  static final HashMap<String, Cache> cache = new HashMap<String, Cache>();
  static final String USER = "user";
//  static final String BUDGET = "budget";
//  static final String ACCOUNT = "account";

  static {
    // userCache the username in memory for a few seconds or so for those fast
    // queries.
    for (String s : new String[]{USER}) { //, BUDGET, ACCOUNT}) {
      cache.put
          (s, new Cache(s, cacheSize, false, false, cacheTtl, cacheTti));
      CacheManager.create(Security.class.getResource("ehcache.xml"))
          .addCache(cache.get(s));
    }
  }

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
          .addAnnotatedClass(AllocationSetting.class)
          .addAnnotatedClass(CategoryAllocationSetting.class)
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

  /**
   * This takes a detached criteria query, probably provided by the client, and
   * returns the list of results.
   *
   * @param dc The detached criteria
   *
   * @return List
   */
  @SuppressWarnings({"unchecked"})
  public List detachedCriteriaQuery(DetachedCriteria dc) {
    logger.info(dc.toString());

    List l = dc.getExecutableCriteria(getCurrentSession())
        .setCacheable(true)
        .list();

    // evict if these are Identifiable objects.
    if (l.size() > 0 && l.get(0) instanceof Identifiable)
      evict(l);

//    BackgroundList bl = new BackgroundList(l);

    return l;
  }

  @SuppressWarnings({"unchecked"})
  public static <T extends Serializable, S extends Serializable> T getIdentifier(
      Identifiable<T, S> c) {
    return (T)getSessionFactory().getClassMetadata(c.getClass())
        .getIdentifier(c, EntityMode.POJO);
  }

  public static void setIdentifier(Serializable c, Serializable value) {
    getSessionFactory().getClassMetadata(c.getClass())
        .setIdentifier(c, value, EntityMode.POJO);
  }

  @SuppressWarnings({"unchecked"})
  public static <T extends Serializable, S extends Serializable> S getVersion(
      Identifiable<T, S> c) {
    return (S)getSessionFactory().getClassMetadata(c.getClass())
        .getVersion(c, EntityMode.POJO);
  }


  public <T extends Identifiable> void delete(T i) {
    getCurrentSession().delete(i);
  }

  public <T extends Identifiable> void delete(T[] is) {
    this.delete(listify(is));
  }

  public <T extends Identifiable> void delete(Iterable<T> l) {
    for (T i : l) delete(i);
  }

  protected <T extends Identifiable> void evict(T i) {
    getCurrentSession().evict(i);
  }

  protected <T extends Identifiable> void evict(T[] is) {
    this.evict(listify(is));
  }

  public <T extends Identifiable> void evict(Iterable<T> l) {
    for (T i : l) evict(i);
  }

  @SuppressWarnings("unchecked")
  public <T extends Identifiable> T get(Class<T> t, Serializable id) {
    return (T)getCurrentSession().get(t, id);
  }

  public <T extends Identifiable> List<T> getList(Class<T> t,
                                                  Serializable[] ids) {
    return getList(t, listify(ids));
  }

  public <T extends Identifiable> List<T> getList(Class<T> t,
                                                  Iterable<Serializable> ids) {
    List<T> out = new ArrayList<T>();
    for (Serializable id : ids) out.add(get(t, id));
    return out;
  }

  @SuppressWarnings("unchecked")
  public <T extends Identifiable> List<T> list(
      Class<T> t,
      List<Order> orderby,
      Criterion... crits) {
    Criteria criteria = getCurrentSession().createCriteria(t);
    for (Criterion crit : crits) criteria.add(crit);
    if (orderby != null) for (Order o : orderby) criteria.addOrder(o);
    criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
    return (List<T>)criteria.list();
  }

  public <T extends Identifiable> List<T> list(
      Class<T> t,
      Criterion... crits) {
    return list(t, null, crits);
  }

  @SuppressWarnings("unchecked")
  public <T extends Identifiable> T load(Class<T> t, Serializable id) {
    return (T)getCurrentSession().load(t, id);
  }

  public <T extends Identifiable> List<T> loadList(Class<T> t,
                                                   Serializable... ids) {
    return loadList(t, listify(ids));
  }

  public <T extends Identifiable> List<T> loadList(Class<T> t,
                                                   Iterable<Serializable> ids) {
    List<T> out = new ArrayList<T>();
    for (Serializable id : ids) out.add(load(t, id));
    return out;
  }

  @SuppressWarnings("unchecked")
  public <T extends Identifiable> T merge(T i) {
    return (T)getCurrentSession().merge(i);
  }

  public <T extends Identifiable> List<T> mergeList(T[] is) {
    return mergeList(listify(is));
  }

  public <T extends Identifiable> List<T> mergeList(Iterable<T> is) {
    List<T> out = new ArrayList<T>();
    for (T i : is) out.add(merge(i));
    return out;
  }


  public <T extends Identifiable> void refresh(T i) {
    getCurrentSession().refresh(i);
  }

  public <T extends Identifiable> void refresh(T[] is) {
    refresh(listify(is));
  }

  public <T extends Identifiable> void refresh(Iterable<T> is) {
    for (T i : is) refresh(i);
  }

  public <T extends Identifiable> Serializable save(T o) {
    return getCurrentSession().save(o);
  }

  public <T extends Identifiable> Serializable saveOrUpdate(T o) {
    if (o.getId() == null)
      return getCurrentSession().save(o);
    else {
      getCurrentSession().update(o);
      return o.getId();
    }
  }

  public <T extends Identifiable> List<Serializable> saveOrUpdateList(T[] os) {
    return saveOrUpdateList(listify(os));
  }

  public <T extends Identifiable> List<Serializable> saveOrUpdateList(
      Iterable<T> os) {
    List<Serializable> out = new ArrayList<Serializable>();
    for (T o : os) out.add(saveOrUpdate(o));
    return out;
  }

  public <T extends Identifiable> void update(T o) {
    getCurrentSession().update(o);
  }

  public <T extends Identifiable> void update(T[] os) {
    update(listify(os));
  }

  public <T extends Identifiable> void update(Iterable<T> os) {
    for (T o : os) update(o);
  }

  User getUser(String username) throws DataException {
    User user;

    // if we've already retrieved the user for this thread, just use that.
//    user = ThreadInfo.getUser();
//    if (user != null) return user;

    // if it exists in the userCache, retrieve it.
    Element ue = cache.get(USER).get(username);
    if (ue != null) {
      user = (User)ue.getValue();
      ThreadInfo.setUser(user);
      logger.debug("yanked \"" + username + "\" from ehcache");
    } else {
      // if we're here, we didn't have it in the threadlocal nor userCache.
      // we'll have to ask hibernate.
      List<User> users = list(User.class, Restrictions.eq("name", username));

      if (users.isEmpty())
        throw new DataException(EnvelopeException.Type.Invalid_User,
                                "User " + username + " is invalid.");
      user = users.get(0);
      cache.get(USER).put(new Element(username, user));
      ThreadInfo.setUser(user);
    }
    return (user);
  }

  User getUser() throws IllegalStateException {
    User user = ThreadInfo.getUser();
    if (user == null)
      throw new IllegalStateException("user hasn't been set for this thread");
    return user;
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


  <T> Iterable<T> listify(T[] is) {
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

