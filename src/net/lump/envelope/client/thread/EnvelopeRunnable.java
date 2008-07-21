package us.lump.envelope.client.thread;

/**
 * A runnable with a status identifier.
 *
 * @author Troy Bowman
 * @version $Revision: 1.2 $
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

  public abstract void run();
}
