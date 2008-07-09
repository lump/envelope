package us.lump.envelope.entity;

import us.lump.envelope.server.dao.DAO;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Entity objects which contain an identification field.
 *
 * @author Troy Bowman
 * @version $Id: Identifiable.java,v 1.4 2008/07/09 04:20:02 troy Test $
 */
public abstract class Identifiable <T extends Serializable, S extends Serializable>
    implements Serializable, Stampable<S> {

  /**
   * Reflectively finds the object which identifies this object.  May be
   * overidden.
   *
   * @return Serializable
   */
  @SuppressWarnings({"unchecked"})
  public T getId() {
    return DAO.getIdentifier(this);
  }

  /**
   * Sets the object which identifies this object.
   *
   * @param id Serializable
   */
  public void setId(T id) {
    DAO.setIdentifier(this, id);
  }

  /**
   * Get the timestamp of this entity.
   *
   * @return Timestamp
   */
  @SuppressWarnings({"unchecked"})
  public S getStamp() {
    return (S)DAO.getVersion(this);
  }
}
