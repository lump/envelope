package net.lump.envelope.shared.entity;

import net.lump.envelope.server.dao.DAO;

import java.io.Serializable;

/**
 * Entity objects which contain an identification field.
 *
 * @author Troy Bowman
 * @version $Id: Identifiable.java,v 1.2 2009/10/02 22:06:23 troy Exp $
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
