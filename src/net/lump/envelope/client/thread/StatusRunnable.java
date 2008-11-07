package us.lump.envelope.client.thread;

/**
 * A runnable with a status identifier.
 *
 * @author Troy Bowman
 * @version $Id: StatusRunnable.java,v 1.1 2008/11/07 23:31:06 troy Test $
 */
public abstract class StatusRunnable implements java.lang.Runnable {

  StatusElement element = null;

  private StatusRunnable() {}

  public StatusRunnable(String statusMessage) {
    element = new StatusElement(statusMessage);
  }

  public StatusElement getElement() {
    return element;
  }

  public void setStatusMessage(String statusMessage) {
    element.setValue(statusMessage);
  }

  public abstract void run();
}
