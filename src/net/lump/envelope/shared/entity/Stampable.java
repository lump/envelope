package us.lump.envelope.shared.entity;

import java.io.Serializable;

/**
 * Created by IntelliJ IDEA. User: troy Date: Jul 8, 2008 Time: 9:13:44 PM To
 * change this template use File | Settings | File Templates.
 */
public interface Stampable <T extends Serializable> {
  /**
   * Get the timestamp of this entity.
   *
   * @return Timestamp
   */
  public T getStamp();
}
