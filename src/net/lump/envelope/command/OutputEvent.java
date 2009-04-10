package us.lump.envelope.command;

import java.io.Serializable;
import java.util.EventObject;

/**
 * A command output event.
 *
 * @author troy
 * @version $Id: OutputEvent.java,v 1.2 2009/04/10 22:49:28 troy Exp $
 */
public class OutputEvent extends EventObject {
  Serializable payload;
  Long index;
  Long total;

  public OutputEvent(Object source, Long total, Long index, Serializable payload) {
    super(source);
    this.total = total;
    this.index = index;
    this.payload = payload;
  }

  public Serializable getPayload() {
    return payload;
  }

  public Long getIndex() {
    return index;
  }

  public Long getTotal() {
    return total;
  }
}
