package net.lump.envelope.shared.exception;

/**
 * Tell client stuff to stop processing.
 */
public class AbortException extends Exception {
  public AbortException() {
  }

  public AbortException(String message) {
    super(message);
  }

  public AbortException(String message, Throwable cause) {
    super(message, cause);
  }

  public AbortException(Throwable cause) {
    super(cause);
  }
}
