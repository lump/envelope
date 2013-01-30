package net.lump.envelope.shared.command;

import java.io.Serializable;
import java.util.EventObject;

/**
 * A command output event.
 *
 * @author troy
 * @version $Id: OutputEvent.java,v 1.3 2010/09/20 23:18:23 troy Exp $
 */
public class OutputEvent extends EventObject {
  Serializable payload;
  Long index;
  Long total;
  Long bytesRead;
  Double bytesPerSecond;

  public OutputEvent(Object source, Long total, Long index, Serializable payload, Long bytesRead, Double bytesPerSecond) {
    super(source);
    this.total = total;
    this.index = index;
    this.payload = payload;
    this.bytesRead = bytesRead;
    this.bytesPerSecond = bytesPerSecond;
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

  public Long getBytesRead() {
    return bytesRead;
  }

  public Double getBytesPerSecond() {
    return bytesPerSecond;
  }
}
