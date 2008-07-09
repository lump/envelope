package us.lump.envelope.server.dao;

import us.lump.envelope.entity.Identifiable;

import java.io.Serializable;

/** Generic DAO. */
public class Generic extends DAO {

  @Override
  public <T extends Identifiable> T get(Class<T> t, Serializable id) {
    T obj = super.get(t, id);
    super.evict(obj);
    return obj;
  }
}
