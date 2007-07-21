package us.lump.envelope.server.dao;

import org.apache.log4j.Logger;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.SessionException;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Order;
import org.hibernate.impl.SessionImpl;
import us.lump.envelope.entity.*;
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
 * @version $Id: DAO.java,v 1.1 2007/07/21 20:15:04 troy Exp $
 */
public abstract class DAO {
  final Logger logger;

  private static SessionFactory sessionFactory = null;

  {
    logger = Logger.getLogger(this.getClass());

    if (!this.isActive()) begin();
  }

  /**
   * Initialize the session factory in the DAO.
   */
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
    // A Session is opened when getCurrentSession() is called for the first time and
    // closed when the transaction ends. It is also flushed automatically before the
    // transaction commits. You can call getCurrentSession() as often and anywhere you
    // want as long as the transaction runs.
    return getSessionFactory().getCurrentSession();
  }


  <T extends Identifiable> void delete(T... is) {
    this.delete(listify(is));
  }

  <T extends Identifiable> void delete(Iterable<T> l) {
    for (T i : l) getCurrentSession().delete(i);
  }

  <T extends Identifiable> void evict(T... is) {
    this.evict(listify(is));
  }

  <T extends Identifiable> void evict(Iterable<T> l) {
    for (T i : l) getCurrentSession().evict(i);
  }

  @SuppressWarnings("unchecked")
      <T extends Identifiable> T get(Class<T> t, Serializable id) {
    return (T)getCurrentSession().get(t, id);
  }

  <T extends Identifiable> List<T> getList(Class<T> t, Serializable... ids) {
    return getList(t, listify(ids));
  }

  <T extends Identifiable> List<T> getList(Class<T> t, Iterable<Serializable> ids) {
    List<T> out = new ArrayList<T>();
    for (Serializable id : ids) out.add(get(t, id));
    return out;
  }

  @SuppressWarnings("unchecked")
      <T extends Identifiable> List<T> list(Class<T> t, List<Order> orderby, Criterion... crits) {
    Criteria criteria = getCurrentSession().createCriteria(t);
    for (Criterion crit : crits) criteria.add(crit);
    if (orderby != null) for (Order o : orderby) criteria.addOrder(o);
    return (List<T>)criteria.list();
  }

  <T extends Identifiable> List<T> list(Class<T> t, Criterion... crits) {
    return list(t, null, crits);
  }

  @SuppressWarnings("unchecked")
      <T extends Identifiable> T load(Class<T> t, Serializable id) {
    return (T)getCurrentSession().load(t, id);
  }

  <T extends Identifiable> List<T> loadList(Class<T> t, Serializable... ids) {
    return loadList(t, listify(ids));
  }

  <T extends Identifiable> List<T> loadList(Class<T> t, Iterable<Serializable> ids) {
    List<T> out = new ArrayList<T>();
    for (Serializable id : ids) out.add(load(t, id));
    return out;
  }

  @SuppressWarnings("unchecked")
      <T extends Identifiable> T merge(T i) {
    return (T)getCurrentSession().merge(i);
  }

  <T extends Identifiable> List<T> mergeList(T... is) {
    return mergeList(listify(is));
  }

  <T extends Identifiable> List<T> mergeList(Iterable<T> is) {
    List<T> out = new ArrayList<T>();
    for (T i : is) out.add(merge(i));
    return out;
  }

  <T extends Identifiable> void refresh(T... is) {
    refresh(listify(is));
  }

  <T extends Identifiable> void refresh(Iterable<T> is) {
    for (T i : is) getCurrentSession().refresh(i);
  }

  <T extends Identifiable> Serializable save(T os) {
    return getCurrentSession().save(os);
  }

  <T extends Identifiable> Serializable saveOrUpdate(T o) {
    if (o.getId() == null)
      return getCurrentSession().save(o);
    else {
      getCurrentSession().update(o);
      return o.getId();
    }
  }

  <T extends Identifiable> List<Serializable> saveOrUpdateList(T... os) {
    return saveOrUpdateList(listify(os));
  }

  <T extends Identifiable> List<Serializable> saveOrUpdateList(Iterable<T> os) {
    List<Serializable> out = new ArrayList<Serializable>();
    for (T o : os) out.add(saveOrUpdate(o));
    return out;
  }

  <T extends Identifiable> void update(T... os) {
    update(listify(os));
  }

  <T extends Identifiable> void update(Iterable<T> os) {
    for (T o : os) getCurrentSession().update(o);
  }

  void begin() {
    getTransaction().begin();
    logger.debug("began transaction");
  }

  void commit() {
    getTransaction().commit();
    logger.debug("committed transaction");
  }

  void rollback() {
    getTransaction().rollback();
    logger.debug("rolled back transaction");
  }

  void flush() {
    getCurrentSession().flush();
    logger.debug("flushed session");
  }

  boolean isActive() {
    return getTransaction().isActive();
  }

  boolean wasCommitted() {
    return getTransaction().wasCommitted();
  }

  boolean wasRolledBack() {
    return getTransaction().wasRolledBack();
  }

  public void close() {
    getCurrentSession().close();
    logger.debug("closed transaction");
  }

  /**
   * Get the current Transsaction.
   * @return Transaction
   */
  public org.hibernate.Transaction getTransaction() {
    return getCurrentSession().getTransaction();
  }

  Connection getConnection() throws SQLException {
    Connection c = ((SessionImpl)getCurrentSession()).getJDBCContext().borrowConnection();
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
      if (isActive() && !wasRolledBack()) { flush(); commit(); }
    } finally {
      super.finalize();
    }
  }
}

