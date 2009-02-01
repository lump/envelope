package us.lump.envelope.client.portal;

import org.hibernate.criterion.DetachedCriteria;
import us.lump.envelope.Command;
import us.lump.envelope.entity.Identifiable;
import us.lump.envelope.exception.AbortException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** A portal for hibernate operations. */
@SuppressWarnings({"unchecked"})
public class HibernatePortal extends Portal {

  public List detachedCriteriaQuery(DetachedCriteria... dcs)
      throws AbortException {

    if (dcs.length == 0)
      throw new IllegalArgumentException("need one or more arguments");
    if (dcs.length == 1)
      return (List)invoke(new Command(Command.Name.detachedCriteriaQuery, dcs[0]));
    else {
      List<Command> dcl = new ArrayList<Command>();
      for (DetachedCriteria dc : dcs)
        dcl.add(new Command(Command.Name.detachedCriteriaQuery, dc));
      return (List)invoke(dcl);
    }
  }

  public <T extends Identifiable> T get(Class<T> cless, Serializable id)
      throws AbortException {
    return (T)invoke(new Command(Command.Name.get, cless, id));
  }

}
