package us.lump.envelope.server.exception;

/**
 * Created by IntelliJ IDEA. User: troy Date: Jul 4, 2008 Time: 5:07:12 PM To
 * change this template use File | Settings | File Templates.
 */
public class DataException extends EnvelopeException {
  
  public DataException(Type type, String message, Throwable cause) {
    super(type, message, cause);
  }

  public DataException(Type type, String message) {
    super(type, message);
  }

  public DataException(Type type, Throwable cause) {
    super(type, cause);
  }

  public DataException(Type type) {
    super(type);
  }
}
