package us.lump.envelope.shared.command;

import java.util.EventListener;

/**
 * A listener that can handle CommandOutputEvents.
 *
 * @author troy
 * @version $Id: OutputListener.java,v 1.1 2009/07/13 17:21:44 troy Exp $
 */
public interface OutputListener extends EventListener {
  public void commandOutputOccurred(OutputEvent event);
}
