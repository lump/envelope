package us.lump.envelope.server.rmi;

import org.apache.log4j.Logger;
import us.lump.envelope.Command;
import us.lump.envelope.server.dao.DAO;
import us.lump.envelope.server.dao.Security;
import us.lump.envelope.server.exception.AuthenticationException;
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
 * @version $Id: Controlled.java,v 1.5 2007/09/09 07:17:10 troy Exp $
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
    super();
  }

  /**
   * Invoke a command. This is the central method where every command must pass.
   * Since this causes centralization, we can check for security as defined by
   * the command, and validate sessions if necessary.  We can also catch errors
   * and handle graceful closing or rollbacks of transactions.
   *
   * @param commands one more more commands
   *
   * @return Object if there are more than one command, the object will be a
   *         List of objects.
   *
   * @throws RemoteException
   */
  @SuppressWarnings({"LoopStatementThatDoesntLoop"})
  public Serializable invoke(Command... commands) throws RemoteException {


    ArrayList<Serializable> returnList = new ArrayList<Serializable>();

    for (Command command : commands) {
      logger.debug("Received command " + command.getName().name());

      try {
        // if a session is required, force check authorization first
        if (command.getName().isSessionRequired()
            && !(new Security()).validateSession(command))
          throw new AuthenticationException("Invalid session");
      } catch (Exception e) {
        logger.warn("error in session validation", e);
        throw new RemoteException("error in session validation", e);
      }

      // session management should be done now, so we can now dispatch
      returnList.add(dispatch(command));
    }

    // return the single return object if the list size is 1.
    return returnList.size() == 1
           ? returnList.get(0)
           // return the list if there are more than one
           : returnList.size() > 1
             ? returnList
             // else, return null if it's empty
             : null;
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
          dao.getClass().getDeclaredMethod(command.getName().name(), paramNames)
              // invoke the method on a new instance of the class with the arguments
              .invoke(dao, args);

      // make sure the return value is Serializable.
      if (returnValue instanceof Serializable)
        return (Serializable)returnValue;
      else
        throw new RemoteException("return value is not serializable");

    } catch (Exception e) {
      // rollback the transaction if it is active.
      if (dao != null
          && dao.getTransaction().isActive()
          && !dao.getTransaction().wasRolledBack()) {
        dao.getTransaction().rollback();
        logger.warn("transaction was rolled back");
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
      if (e instanceof InvocationTargetException)
        throw new IllegalArgumentException(
            "Could not invoke " + command.getName().name());

      throw (RemoteException)e;
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
