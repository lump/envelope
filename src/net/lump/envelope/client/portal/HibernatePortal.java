package us.lump.envelope.client.portal;

import org.hibernate.criterion.DetachedCriteria;
import us.lump.envelope.Command;
import us.lump.envelope.exception.EnvelopeException;
import us.lump.envelope.entity.Identifiable;
import us.lump.lib.util.IncrementalArray;

import java.io.Serializable;
import java.util.List;

/** A portal for hibernate operations. */
@SuppressWarnings({"unchecked"})
public class HibernatePortal extends Portal {
  
  public List detachedCriteriaQuery(DetachedCriteria dc)
      throws EnvelopeException {
    IncrementalArray l = (IncrementalArray)invoke(new Command(Command.Name.detachedCriteriaQuery, dc));
    while (l.invalid())
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    return l;
  }

  public <T extends Identifiable> T get(Class<T> i, Serializable id)
    throws EnvelopeException {
    return (T)invoke(new Command(Command.Name.get, i, id));
  }

}
