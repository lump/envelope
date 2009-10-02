package net.lump.envelope.shared.command;

import java.util.EventListener;

/**
 * A listener that can handle CommandOutputEvents.
 *
 * @author troy
 * @version $Id: OutputListener.java,v 1.2 2009/10/02 22:06:23 troy Exp $
 */
public interface OutputListener extends EventListener {
  public void commandOutputOccurred(OutputEvent event);
}
