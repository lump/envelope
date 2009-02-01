package us.lump.envelope.exception;

import java.rmi.RemoteException;

/**
 * An exception for the Envelope application.
 *
 * @author Troy Bowman
 * @version $Id: EnvelopeException.java,v 1.3 2009/02/01 02:33:42 troy Test $
 */
public class EnvelopeException extends RemoteException {

  public enum Type {
    Generic,
    Data,
    Session,
    Command,
    Server,
  }

  public enum Name {
    Generic(Type.Generic),
    Invalid_Credentials(Type.Session),
    Invalid_Session(Type.Session),
    Invalid_User(Type.Session),
    Internal_Server_Error(Type.Server),
    Invalid_Command(Type.Command);

    private Type type;
    private Name(Type type) {
      this.type = type;
    }
    public Type getType() { return type; }
  }

  private Name name = Name.Generic;

  public Type getType() {
    return name.getType();
  }

  public Name getName() {
    return name;
  }

  public EnvelopeException() {
    super();
  }

  public EnvelopeException(String message) {
    super(message);
  }

  public EnvelopeException(String message, Throwable cause) {
    this(Name.Generic, message, cause);
  }

  public EnvelopeException(Name name, String message, Throwable cause) {
    super(message, cause);
    this.name = name;
  }

  public EnvelopeException(Name name, String message) {
    super(message);
    this.name = name;
  }

  public EnvelopeException(Name name, Throwable cause) {
    super(name.toString(), cause);
    this.name = name;
  }

  public EnvelopeException(Name name) {
    super(name.toString());
    this.name = name;
  }
}
