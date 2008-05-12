package us.lump.envelope.server.dao;

import org.hibernate.criterion.DetachedCriteria;

import java.util.List;

/** Generic DAO. */
public class Generic extends DAO {
  public List detachedCriteriaQuery(DetachedCriteria dc) {
    return dc.getExecutableCriteria(getCurrentSession()).list();
  }
}
