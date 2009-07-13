package us.lump.envelope.server.dao;

import org.hibernate.CacheMode;
import org.hibernate.Criteria;
import org.hibernate.ScrollableResults;
import org.hibernate.criterion.DetachedCriteria;
import us.lump.envelope.shared.entity.Identifiable;

import java.io.Serializable;

/** Generic DAO. */
public class Generic extends DAO {

  @Override
  public <T extends Identifiable> T get(Class<T> t, Serializable id) {
    T obj = super.get(t, id);
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
