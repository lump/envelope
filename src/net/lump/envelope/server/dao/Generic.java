package us.lump.envelope.server.dao;

import us.lump.envelope.entity.Identifiable;

import java.io.Serializable;

/** Generic DAO. */
public class Generic extends DAO {

  public <T extends Identifiable> T load(Class<T> t, Serializable id) {
    T entity = super.load(t, id);
    evict(entity);
    return entity;
  }

}
