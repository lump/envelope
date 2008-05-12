package us.lump.envelope.client.portal;

import org.hibernate.criterion.DetachedCriteria;
import us.lump.envelope.Command;

import java.util.List;

/** A portal for hibernate operations. */
@SuppressWarnings({"unchecked"})
public class HibernatePortal extends Portal {

  public List detachedCriteriaQuery(DetachedCriteria dc) {
    return (List)invoke(new Command(Command.Name.detachedCriteriaQuery, dc));
  }
}
