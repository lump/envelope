package us.lump.envelope.server.exception;

/**
 * An exception dealing with Sessions.
 *
 * @author Troy Bowman
 * @version $Id: SessionException.java,v 1.3 2008/07/06 04:14:24 troy Exp $
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
