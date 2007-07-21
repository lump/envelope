package us.lump.envelope.server.exception;

/**
 * An Authentication Exception.
 *
 * @author Troy Bowman
 * @version $Id: AuthenticationException.java,v 1.1 2007/07/21 20:15:04 troy Exp $
 */
public class AuthenticationException extends RuntimeException {
  /**
   * An authentication Exception with a message and cause.
   *
   * @param message of the exception
   * @param cause of the original exception
   */
  public AuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * An authentication Exception with a message.
   * @param message of the exception.
   */
  public AuthenticationException(String message) {
    super(message);
  }

  /**
   * An authentication Excption with a cause.
   *
   * @param cause of the original exception.
   */
  public AuthenticationException(Throwable cause) {
    super(cause);
  }

  /**
   * An authentication exception.
   */
  public AuthenticationException() {
    super();
  }
}
