package us.lump.envelope.client.portal;

import org.hibernate.criterion.DetachedCriteria;
import us.lump.envelope.Command;
import us.lump.envelope.entity.Identifiable;

import java.io.Serializable;
import java.util.List;

/** A portal for hibernate operations. */
@SuppressWarnings({"unchecked"})
public class HibernatePortal extends Portal {

  public List detachedCriteriaQuery(DetachedCriteria dc) {
    return (List)invoke(new Command(Command.Name.detachedCriteriaQuery, dc));
  }

  public <T extends Identifiable> T get(Class<T> i, Serializable id) {
    return (T)invoke(new Command(Command.Name.get, i, id));
  }

}
