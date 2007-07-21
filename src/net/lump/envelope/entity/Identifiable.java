package us.lump.envelope.entity;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * ntity objects which contain an identification field.
 *
 * @author Troy Bowman
 * @version $Id: Identifiable.java,v 1.1 2007/07/21 20:15:04 troy Exp $
 */
public interface Identifiable extends Serializable {
  /**
   * Gets the object which identifies this object.
   * @return Serializable
   */
  public Serializable getId();

  /**
   * Sets the object which identifies this object.
   * @param id Serializable
   */
  public void setId(Serializable id);

  /**
   * Get the timestamp of this entity.
   * @return Timestamp
   */
  public Timestamp getStamp();

  /**
   * Set the Timestamp of this entity.
   * @param stamp Timestamp
   */
  public void setStamp(Timestamp stamp);

}
