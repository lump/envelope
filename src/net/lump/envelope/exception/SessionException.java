package us.lump.envelope.exception;

/**
 * An exception dealing with Sessions.
 *
 * @author Troy Bowman
 * @version $Id: SessionException.java,v 1.1 2008/07/09 08:16:40 troy Exp $
 */
public class SessionException extends EnvelopeException {
  public SessionException(Type type, String message, Throwable cause) {
    super(type, message, cause);
  }

  public SessionException(Type type, String message) {
    super(type, message);
  }

  public SessionException(Type type, Throwable cause) {
    super(type, cause);
  }

  public SessionException(Type type) {
    super(type);
  }
}
