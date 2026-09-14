package net.lump.envelope.server.dao;

import org.hibernate.CacheMode;
import org.hibernate.Criteria;
import org.hibernate.Hibernate;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.DetachedCriteria;
import net.lump.envelope.shared.entity.Identifiable;

import java.io.Serializable;

/** Generic DAO. */
public class Generic extends DAO {

  @Override
  public <T extends Identifiable> T get(Class<T> t, Serializable id) {
    T obj = super.get(t, id);
    // Session.get answers null for a row that is not there, which is an ordinary
    // answer and not an error -- but both initialize() and evict() throw on null,
    // so asking for a deleted id used to come back as a NullPointerException.
    if (obj == null) return null;
    Hibernate.initialize(obj);
    super.evict(obj);
    return obj;
  }

  @Override
  public <T extends Identifiable> T load(Class<T> t, Serializable id) {
    T obj = super.load(t, id);
    Hibernate.initialize(obj);
    super.evict(obj);
    return obj;
  }

  /**
   * This takes a detached criteria query, probably provided by the client, and returns the list of results.
   *
   * @param dc    The detached criteria
   * @param cache whether to turn on query caching
   * @return Scrollable Results
   */
  @SuppressWarnings({"unchecked"})
  public ScrollableResults detachedCriteriaQueryList(DetachedCriteria dc, Boolean cache) {
    logger.info(dc.toString());
    Criteria c = dc.getExecutableCriteria(getCurrentSession());
    if (cache) c = c.setCacheable(cache).setCacheMode(CacheMode.NORMAL);
    return c.scroll();
  }

  /**
   * One cheap query against a table the application cannot work without, for
   * the readiness probe.  Constructing this DAO already opened a session and
   * began a transaction, so reaching here means the pool handed out a
   * connection; the query means the schema is there too.
   */
  public String readyCheck() {
    Number users = (Number)getCurrentSession()
        .createQuery("select count(u) from User u").uniqueResult();
    return "ready: " + users + " users";
  }

  public Serializable detachedCriteriaQueryUnique(DetachedCriteria dc, Boolean cache) {
    logger.info(dc.toString());
    Criteria c = dc.getExecutableCriteria(getCurrentSession());
    if (cache) c = c.setCacheable(true).setCacheMode(CacheMode.NORMAL);
    Serializable s = (Serializable)c.uniqueResult();

    // evict if this is an Identifiable
    if (s instanceof Identifiable) evict((Identifiable)s);
    return s;
  }
}
