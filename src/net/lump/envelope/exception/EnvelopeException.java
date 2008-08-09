package us.lump.envelope.exception;

import java.rmi.RemoteException;

/**
 * An exception for the Envelope application.
 *
 * @author Troy Bowman
 * @version $Id: EnvelopeException.java,v 1.2 2008/08/09 03:31:02 troy Exp $
 */
abstract public class EnvelopeException extends RemoteException {
  public enum Type {
    Undefined,
    Invalid_Credentials,
    Invalid_Session,
    Invalid_User,
    Invalid_Command
  }

  private Type type = Type.Undefined;

  public Type getType() {
    return type;
  }

  public EnvelopeException() {
    super();
  }

  public EnvelopeException(String message) {
    super(message);
  }

  public EnvelopeException(String message, Throwable cause) {
    super(message, cause);
  }

  public EnvelopeException(Type type, String message, Throwable cause) {
    super(message, cause);
    this.type = type;
  }

  public EnvelopeException(Type type, String message) {
    super(message);
    this.type = type;
  }

  public EnvelopeException(Type type, Throwable cause) {
    super(type.toString(), cause);
    this.type = type;
  }

  public EnvelopeException(Type type) {
    super(type.toString());
    this.type = type;
  }
}
