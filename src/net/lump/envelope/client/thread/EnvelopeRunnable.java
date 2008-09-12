package us.lump.envelope.client.thread;

/**
 * A runnable with a status identifier.
 *
 * @author Troy Bowman
 * @version $Id: EnvelopeRunnable.java,v 1.4 2008/09/12 00:21:06 troy Exp $
 */
public abstract class EnvelopeRunnable implements java.lang.Runnable {

  StatusElement element = null;

  private EnvelopeRunnable() {}

  public EnvelopeRunnable(String statusMessage) {
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
