package us.lump.envelope.entity;

import us.lump.envelope.server.dao.DAO;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Entity objects which contain an identification field.
 *
 * @author Troy Bowman
 * @version $Id: Identifiable.java,v 1.3 2008/05/13 01:25:31 troy Exp $
 */
public abstract class Identifiable<T extends Serializable>
    implements Serializable {
  /**
   * Reflectively finds the object which identifies this object.  May be
   * overidden.
   *
   * @return Serializable
   */
  @SuppressWarnings({"unchecked"})
  public T getId() {
    return (T)DAO.getIdentifier(this);
  }

  /**
   * Sets the object which identifies this object.
   *
   * @param id Serializable
   */
  public void setId(Serializable id) {
    DAO.setIdentifier(this, id);
  }

  /**
   * Get the timestamp of this entity.
   *
   * @return Timestamp
   */
  public Timestamp getStamp() {
    return (Timestamp)DAO.getVersion(this);
  }

  ;

}
