package us.lump.lib.util;

import java.util.EventListener;

/**
 * A listener who listends to BackgroundList Events.
 *
 * @author Troy Bowman
 * @version $Id: BackgroundListListener.java,v 1.1 2008/08/09 03:31:02 troy Test $
 */

public interface BackgroundListListener extends EventListener {
  public void backgroundListEventOccurred(BackgroundListEvent event);
}
