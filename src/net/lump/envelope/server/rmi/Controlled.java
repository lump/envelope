package us.lump.envelope.server.rmi;

import org.apache.log4j.Logger;
import us.lump.envelope.Command;
import us.lump.envelope.exception.DataException;
import us.lump.envelope.exception.EnvelopeException;
import us.lump.envelope.exception.SessionException;
import us.lump.envelope.server.dao.DAO;
import us.lump.envelope.server.dao.Security;
import us.lump.lib.util.Span;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;

/**
 * The methods used by the controller.
 *
 * @author Troy Bowman
 * @version $Id: Controlled.java,v 1.15 2008/09/18 05:50:07 troy Exp $
 */
public class Controlled extends UnicastRemoteObject implements Controller {
  final Logger logger = Logger.getLogger(Controller.class);
  private static final String DAO_PATH = "us.lump.envelope.server.dao.";

  private static final String SPACE = " ";

  /**
   * This is usally called by the remote registry.
   *
   * @throws RemoteException
   */
  public Controlled() throws RemoteException {
    super(0);
  }

  /**
   * A constructor which doesn't start up UnicastRemoteObject crap.
   *
   * @param blah
   * @throws RemoteException
   */
  public Controlled(Object blah) throws RemoteException {
  }

  /**
   * Invoke a command. This is the central method where every command must pass.
   * Since this causes centralization, we can check for security as defined by
   * the command, and validate sessions if necessary.  We can also catch errors
   * and handle graceful closing or rollbacks of transactions.
   *
   * @param command the command
   *
   * @return an object which must be Serializable for transfer
   *
   *
   * @throws RemoteException
   */
  @SuppressWarnings({"LoopStatementThatDoesntLoop"})
  public Serializable invoke(Command command) throws RemoteException {


    ArrayList<Serializable> returnList = new ArrayList<Serializable>();

    logger.debug("Received command " + command.getName().name());

    try {
      // if a session is required, force check authorization first
      if (command.getName().isSessionRequired()
          && !(new Security()).validateSession(command))
        throw new SessionException(SessionException.Type.Invalid_Session);
    } catch (Exception e) {
      if (e instanceof DataException &&
          ((DataException)e).getType() == EnvelopeException.Type.Invalid_User
          ) {
        throw new SessionException(SessionException.Type.Invalid_Credentials);
      } else {
        logger.warn("error in session validation", e);
        throw new SessionException(SessionException.Type.Invalid_Session);
      }
    }

    // session management should be done now, so we can now dispatch and return
    return dispatch(command);
  }

  /*
   * This method handles the reflection needed to dispatch the Command.
   */
  private Serializable dispatch(Command command) throws RemoteException {
    // start time
    long start = System.currentTimeMillis();

    // the dao object
    DAO dao = null;

    try {
      // parameter names
      Class[] paramNames = new Class[command.getName().getParamTypes().size()];
      // parameter arguments
      Object[] args = new Object[command.getName().getParamTypes().size()];
      // populate the arrays for reflection
      for (int x = 0; x < command.getName().getParamTypes().size(); x++) {
        paramNames[x] = command.getName().getParamType(x);
        args[x] = command.getParam(x);
      }

      // deduce the DAO class name from the facet and create an instance.
      dao = (DAO)Class.forName(
          DAO_PATH + command.getName().getFacet().name()).newInstance();

      // invoke the method and reap the return value.
      Object returnValue =
          // get the method from the command name and parameter names
          dao.getClass().getMethod(command.getName().name(), paramNames)
              // invoke the method on a new instance of the class with the arguments
              .invoke(dao, args);

      // make sure the return value is Serializable.
      if (returnValue instanceof Serializable || returnValue == null)
        return (Serializable)returnValue;
      else
        throw new RemoteException("return value is not serializable");

    } catch (Exception e) {
      // rollback the transaction if it is active.
      if (dao != null
          && dao.getTransaction().isActive()
          && !dao.getTransaction().wasRolledBack()) {
        dao.getTransaction().rollback();
        logger.warn("transaction was rolled back", e);
      }

      //every exception will be caught, logged, and re-thrown to the client.
      logger.fatal("caught exception leaving controller", e);
      if (e instanceof ClassNotFoundException)
        throw new IllegalArgumentException(
            "Bad Facet " + command.getName().getFacet().name(), e);
      if (e instanceof IllegalAccessException
          || e instanceof NoSuchMethodException)
        throw new IllegalArgumentException(
            "Bad Command " + command.getName().name(), e);
      if (e instanceof InstantiationException)
        throw new IllegalArgumentException(
            "Could not instantiate " + command.getName().getFacet().name(), e);
      if (e instanceof InvocationTargetException) {
        if (e.getCause() instanceof EnvelopeException)
          throw (EnvelopeException)e.getCause();
        throw new IllegalArgumentException(
            "Could not invoke " + command.getName().name());
      }

      throw new RemoteException("wrapped throwable", e);
    }
    finally {
      if (dao != null) {
        if (dao.isActive() && !dao.wasRolledBack()) {
          if (dao.isDirty()) dao.flush();
          dao.commit();
        }
        // close the session
        dao.close();
        dao.disconnect();
      }

      logger.info(
          (command.getCredentials() != null
           ? command.getCredentials().getUsername()
           : "no-session")
          + SPACE + command.getName().name() + SPACE
          + ((System.currentTimeMillis() - start)
             / (double)Span.SECOND.millis) + "s");
    }
  }
}
