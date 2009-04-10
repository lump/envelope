package us.lump.envelope.client.portal;

import org.hibernate.criterion.DetachedCriteria;
import us.lump.envelope.command.Command;
import us.lump.envelope.command.OutputListener;
import us.lump.envelope.entity.Identifiable;
import us.lump.envelope.exception.AbortException;

import java.io.Serializable;
import java.util.List;

/** A portal for hibernate operations. */
@SuppressWarnings({"unchecked"})
public class HibernatePortal extends Portal {

  public Serializable detachedCriteriaQueryUnique(DetachedCriteria dc) throws AbortException {
    return invoke(new Command(Command.Name.detachedCriteriaQueryUnique, null,dc));
  }

  public List detachedCriteriaQueryList(DetachedCriteria dc) throws AbortException {
    return (List)invoke(new Command(Command.Name.detachedCriteriaQueryList, null,dc));
  }

  public Serializable detachedCriteriaQueryUnique(DetachedCriteria dc, OutputListener ol) throws AbortException {
    return invoke(new Command(Command.Name.detachedCriteriaQueryUnique, ol, dc));
  }

  public List detachedCriteriaQuery(DetachedCriteria dc, OutputListener ol) throws AbortException {
    return (List)invoke(new Command(Command.Name.detachedCriteriaQueryList, ol, dc));
  }

  public <T extends Identifiable> T get(Class<T> cless, Serializable id)
      throws AbortException {
    return (T)invoke(new Command(Command.Name.get, null, cless, id));
  }
}
