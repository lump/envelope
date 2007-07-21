package us.lump.envelope.server.exception;

/**
 * An exception dealing with Sessions.
 *
 * @author Troy Bowman
 * @version $Id: SessionException.java,v 1.1 2007/07/21 20:15:04 troy Exp $
 */
public class SessionException extends RuntimeException {
  /**
   * A session Exception with a message and cause.
   *
   * @param message of the exception
   * @param cause of the original exception
   */
  public SessionException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * A session Exception with a message.
   * @param message of the exception.
   */
  public SessionException(String message) {
    super(message);
  }

  /**
   * A session Exception with a cause.
   *
   * @param cause of the original exception.
   */
  public SessionException(Throwable cause) {
    super(cause);
  }

  /**
   * A session Exception.
   */
  public SessionException() {
    super();
  }

}
