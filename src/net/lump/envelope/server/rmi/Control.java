package us.lump.envelope.server.rmi;

import org.apache.log4j.Logger;
import us.lump.envelope.Command;
import us.lump.envelope.server.dao.DAO;
import us.lump.envelope.server.dao.Security;
import us.lump.envelope.server.exception.AuthenticationException;

import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;

/**
 * The methods used by the controller.
 *
 * @author Troy Bowman
 * @version $Id: Control.java,v 1.4 2007/08/18 04:49:44 troy Exp $
 */
public class Control extends UnicastRemoteObject implements Controller {
  final Logger logger = Logger.getLogger(Controller.class);
  private static final String DAO_PATH = "us.lump.envelope.server.dao.";

  /**
   * This is usally called by the remote registry.
   *
   * @throws RemoteException
   */
  public Control() throws RemoteException {
    super();
  }

  /**
   * Invoke a command.
   *
   * @param commands one more more commands
   * @return Object if there are more than one command, the object will be a List of objects.
   * @throws RemoteException
   */
  @SuppressWarnings({"LoopStatementThatDoesntLoop"})
  public Object invoke(Command... commands) throws RemoteException {


    ArrayList<Object> returnList = new ArrayList<Object>();

    for (Command command : commands) {
      logger.debug("Received command " + command.getCmd().name());

      try {
        // if a session is required, force check authorization first
        if (command.getCmd().isSessionRequired()
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

  private Object dispatch(Command command) throws RemoteException {
    // the dao object
    DAO dao = null;
    // start time
    long start = System.currentTimeMillis();

    try {
      // parameter names
      Class[] paramNames = new Class[command.getCmd().getParams().size()];
      // parameter arguments
      Object[] args = new Object[command.getCmd().getParams().size()];
      // populate the arrays for reflection
      for (int x = 0; x < command.getCmd().getParams().size(); x++) {
        paramNames[x] = command.getCmd().getParams().get(x).getType();
        args[x] = command.getParam(command.getCmd().getParams().get(x));
      }

      // deduce the DAO class name from the facet and create an instance.
      dao = (DAO) Class.forName(DAO_PATH + command.getCmd().getFacet().name()).newInstance();

      return
              // get the method from the command name and parameter names
              dao.getClass().getDeclaredMethod(command.getCmd().name(), paramNames)
                      // invoke the method on a new instance of the class with the arguments
                      .invoke(dao, args);

    } catch (Exception e) {
      // rollback the transaction if it is active.
      if (dao != null && dao.getTransaction().isActive() && !dao.getTransaction().wasRolledBack()) {
        dao.getTransaction().rollback();
        logger.warn("transaction was rolled back");
      }

      //every exception will be caught, logged, and re-thrown to the client.
      logger.fatal("error on dispatch", e);
      if (e instanceof ClassNotFoundException)
        throw new IllegalArgumentException("Bad Facet " + command.getCmd().getFacet().name(), e);
      if (e instanceof IllegalAccessException || e instanceof NoSuchMethodException)
        throw new IllegalArgumentException("Bad Command " + command.getCmd().name(), e);
      if (e instanceof InstantiationException)
        throw new IllegalArgumentException("Could not instantiate " + command.getCmd().getFacet().name(), e);
      if (e instanceof InvocationTargetException)
        throw new IllegalArgumentException("Could not invoke " + command.getCmd().name());
      throw new RemoteException(e.getMessage(), e);
    }
    finally {
      // close the session
      dao.close();
      logger.debug(command.getCmd().name() + " "
              + (command.getCredentials() != null ? command.getCredentials().getUsername() + " " : "")
              + ((System.currentTimeMillis() - start) / 1000D) + "s");
    }
  }
}
