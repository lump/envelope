package net.lump.envelope.server;

import org.apache.log4j.Logger;
import org.hibernate.ScrollableResults;
import net.lump.envelope.server.dao.DAO;
import net.lump.envelope.server.dao.Security;
import net.lump.envelope.shared.command.Command;
import net.lump.envelope.shared.exception.EnvelopeException;
import static net.lump.envelope.shared.exception.EnvelopeException.Name;
import static net.lump.envelope.shared.exception.EnvelopeException.Name.Invalid_Session;
import net.lump.lib.util.Interval;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

/**
 * The methods used by the controller.
 *
 * @author Troy Bowman
 * @version $Id: Controller.java,v 1.10 2010/07/28 04:25:04 troy Exp $
 */
public class Controller {
  final Logger logger = Logger.getLogger(Controller.class);
  private static final String DAO_PATH = "net.lump.envelope.server.dao.";
  private static final String SPACE = " ";
  private HttpServletResponse rp;
  private OutputStream os;

  @SuppressWarnings({"UnusedDeclaration"})
  private Controller() {}

  /** blah. */
  public Controller(HttpServletResponse rp, OutputStream os) {
    this.rp = rp;
    this.os = os;
  }

  /**
   * Invoke a command. This is the central method where every command must pass. Since this causes centralization, we can check for
   * security as defined by the command, and validate sessions if necessary.  We can also catch errors and handle graceful closing
   * or rollbacks of transactions.
   *
   * @param command the command
   *
   * @return an object which must be Serializable for transfer
   *
   * @throws RemoteException
   */
  @SuppressWarnings({"LoopStatementThatDoesntLoop"})
  public void invoke(Command command) throws RemoteException {

    logger.debug("Received command " + command.getName().name());

    try {
      // if a session is required, force check authorization first
      if (command.getName().isSessionRequired() && !(new Security()).validateSession(command)) {
        throw new EnvelopeException(Invalid_Session);
      }
    } catch (IOException e) {
      if (e instanceof RemoteException) throw (RemoteException)e;
      logger.error(e);
      throw new EnvelopeException(Name.Internal_Server_Error, e);
    } catch (SignatureException e) {
      logger.error(e);
      throw new EnvelopeException(Name.Internal_Server_Error, e);
    } catch (InvalidKeyException e) {
      logger.error(e);
      throw new EnvelopeException(Name.Internal_Server_Error, e);
    } catch (InvalidKeySpecException e) {
      logger.error(e);
      throw new EnvelopeException(Name.Internal_Server_Error, e);
    } catch (NoSuchAlgorithmException e) {
      logger.error(e);
      throw new EnvelopeException(Name.Internal_Server_Error, e);
    }

    // session management should be done now, so we can now dispatch and return
    dispatch(command);
  }

  /*
   * This method handles the reflection needed to dispatch the Command.
   */
  private void dispatch(Command command) throws RemoteException {
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
      dao = (DAO)Class.forName(DAO_PATH + command.getName().getFacet().name()).newInstance();

      Object returnValue = null;
      try {
        // invoke the method and reap the return value.
        returnValue =
            // get the method from the command name and parameter names
            dao.getClass().getMethod(command.getName().name(), paramNames)
                // invoke the method on a new instance of the class with the arguments
                .invoke(dao, args);
      } catch (InvocationTargetException ite) {
        Throwable cause = ite.getCause();
        if (cause instanceof RemoteException && returnValue == null) {
          throw (RemoteException)cause;
        }
        else throw ite;
      }

      // we have to handle the results here before the transaction is over.
      if (returnValue instanceof ScrollableResults) {
        ScrollableResults sr = (ScrollableResults)returnValue;
        try {sr.afterLast();
          sr.last();
          int count = sr.getRowNumber() + 1;
          rp.addHeader("Single-Object", Boolean.FALSE.toString());
          rp.addIntHeader("Object-Count", count);
          sr.beforeFirst();
          ObjectOutputStream oos = new ObjectOutputStream(os);
          if (count > 100)
            while (sr.next())
              oos.writeObject(sr.get().length == 1 ? sr.get()[0] : sr.get());
          else
            while (sr.next()) {
              oos.writeObject(sr.get().length == 1 ? sr.get()[0] : sr.get());
              oos.flush();
            }

          oos.flush();
        } finally {
          sr.close();
        }

      }
      else if (returnValue instanceof List) {
        rp.addHeader("Single-Object", Boolean.FALSE.toString());
        List l = (List)returnValue;
        rp.addIntHeader("Object-Count", l.size());
        ObjectOutputStream oos = new ObjectOutputStream(os);
        for (Object o : l) {
          oos.writeObject(o);
        }
        oos.flush();
      }
      else if (returnValue instanceof Serializable || returnValue == null) {
        rp.addHeader("Single-Object", Boolean.TRUE.toString());
        rp.addIntHeader("Object-Count", 1);
        ObjectOutputStream oos = new ObjectOutputStream(os);
        oos.writeObject(returnValue);
        oos.flush();
      }
      else throw new RemoteException("return value is not serializable");

    } catch (Exception e) {
      // rollback the transaction if it is active.
      if (dao != null && dao.getTransaction().isActive() && !dao.getTransaction().wasRolledBack()) {
        dao.getTransaction().rollback();
        logger.warn("transaction was rolled back", e);
      }

      //every exception will be caught, logged, and re-thrown to the client.
      logger.fatal("caught exception leaving controller", e);

      if (e instanceof ClassNotFoundException)
        throw new IllegalArgumentException("Bad Facet " + command.getName().getFacet().name(), e);
      if (e instanceof IllegalAccessException || e instanceof NoSuchMethodException)
        throw new IllegalArgumentException("Bad Command " + command.getName().name(), e);
      if (e instanceof InstantiationException)
        throw new IllegalArgumentException("Could not instantiate " + command.getName().getFacet().name(), e);
      if (e instanceof InvocationTargetException) {
        if (e.getCause() instanceof EnvelopeException) throw (EnvelopeException)e.getCause();
        throw new IllegalArgumentException("Could not invoke " + command.getName().name(), e.getCause());
      }

      throw new RemoteException("wrapped throwable", e);
    } finally {
      if (dao != null) {
        if (dao.isActive() && !dao.wasRolledBack()) {
          if (dao.isDirty()) dao.flush();
          dao.commit();
        }
        // close the session
        dao.close();
        dao.disconnect();
      }

      logger.info((command.getCredentials() != null ? command.getCredentials().getUsername() : "no-session") + SPACE
          + command.getName().name() + SPACE + Interval.span(start, System.currentTimeMillis()));
    }
  }
}