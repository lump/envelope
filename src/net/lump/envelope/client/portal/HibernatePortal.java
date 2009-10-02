package net.lump.envelope.client.portal;

import org.hibernate.criterion.DetachedCriteria;
import net.lump.envelope.shared.command.Command;
import net.lump.envelope.shared.command.OutputListener;
import net.lump.envelope.shared.entity.Identifiable;
import net.lump.envelope.shared.exception.AbortException;

import java.io.Serializable;
import java.util.List;

/** A portal for hibernate operations. */
@SuppressWarnings({"unchecked"})
public class HibernatePortal extends Portal {


  public Serializable detachedCriteriaQueryUnique(DetachedCriteria dc) throws AbortException {
    return detachedCriteriaQueryUnique(dc, false);
  }
  public Serializable detachedCriteriaQueryUnique(DetachedCriteria dc, boolean cache) throws AbortException {
    return detachedCriteriaQueryUnique(dc, cache, null);
  }
  public Serializable detachedCriteriaQueryUnique(DetachedCriteria dc, OutputListener ol) throws AbortException {
    return invoke(new Command(Command.Name.detachedCriteriaQueryUnique, ol, dc, false));
  }
  public Serializable detachedCriteriaQueryUnique(DetachedCriteria dc, boolean cache, OutputListener ol) throws AbortException {
    return invoke(new Command(Command.Name.detachedCriteriaQueryUnique, ol, dc, cache));
  }

  public List detachedCriteriaQueryList(DetachedCriteria dc) throws AbortException {
    return detachedCriteriaQueryList(dc, false);
  }
  public List detachedCriteriaQueryList(DetachedCriteria dc, boolean cache) throws AbortException {
    return detachedCriteriaQueryList(dc, cache, null);
  }
  public List detachedCriteriaQueryList(DetachedCriteria dc, OutputListener ol) throws AbortException {
    return detachedCriteriaQueryList(dc, false, ol);
  }
  public List detachedCriteriaQueryList(DetachedCriteria dc, boolean cache, OutputListener ol) throws AbortException {
    return (List)invoke(new Command(Command.Name.detachedCriteriaQueryList, ol, dc, cache));
  }


  public <T extends Identifiable> T get(Class<T> cless, Serializable id)
      throws AbortException {
    return (T)invoke(new Command(Command.Name.get, null, cless, id));
  }
}
