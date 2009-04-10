package us.lump.envelope.command;

import java.util.EventListener;

/**
 * A listener that can handle CommandOutputEvents.
 *
 * @author troy
 * @version $Id: OutputListener.java,v 1.2 2009/04/10 22:49:28 troy Exp $
 */
public interface OutputListener extends EventListener {
  public void commandOutputOccurred(OutputEvent event);
}
