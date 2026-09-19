package net.lump.envelope.shared.command;

import java.util.EventListener;

/**
 * A listener that can handle CommandOutputEvents.
 *
 * @author troy
 */
public interface OutputListener extends EventListener {
  public void commandOutputOccurred(OutputEvent event);
}
