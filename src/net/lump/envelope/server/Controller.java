package net.lump.envelope.server;

import org.apache.log4j.Logger;
import org.hibernate.ScrollableResults;
import net.lump.envelope.server.dao.DAO;
import net.lump.envelope.shared.command.Command;
import net.lump.envelope.shared.exception.EnvelopeException;
import static net.lump.envelope.shared.exception.EnvelopeException.Name.Invalid_Session;
import static net.lump.envelope.shared.exception.EnvelopeException.Name.Permission_Denied;
import net.lump.envelope.shared.command.security.Permission;
import net.lump.envelope.shared.entity.User;
import net.lump.lib.util.Interval;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.util.List;

/**
 * The methods used by the controller.
 *
 * @author Troy Bowman
 */
public class Controller {
  final Logger logger = Logger.getLogger(Controller.class);
  private static final String DAO_PATH = "net.lump.envelope.server.dao.";
  private static final String SPACE = " ";
  private HttpServletResponse rp;
  private String authenticatedUser;
  private OutputStream os;

  @SuppressWarnings({"UnusedDeclaration"})
  private Controller() {}

  /** blah. */
  public Controller(HttpServletResponse rp, OutputStream os) {
    this.rp = rp;
    this.os = os;
  }

  /**
   * Invoke a command. This is the central method where every command must pass, which is what lets us enforce that a command
   * requiring a session actually has one, and handle graceful closing or rollbacks of transactions.
   *
   * @param command           the command
   * @param authenticatedUser the user the servlet authenticated, or null for the commands that need no session
   *
   * @return an object which must be Serializable for transfer
   *
   * @throws RemoteException
   */
  public void invoke(Command command, String authenticatedUser) throws RemoteException {

    // Name the command on the thread for the log, so that every line logged
    // while it runs -- DAO's "began transaction", Hibernate's, HikariCP's --
    // says who asked for what.  RequestContextFilter takes it off again when
    // the request is done.
    ThreadInfo.setCommand((authenticatedUser != null ? authenticatedUser : "no-session") + SPACE
        + command.getName().name());

    logger.debug("Received command " + command.getName().name());

    // Authentication already happened in the servlet, which had the wire bytes
    // the signature is computed over.  All that is left is to insist that a
    // command needing a session actually got one.
    if (command.getName().isSessionRequired() && authenticatedUser == null) {
      throw new EnvelopeException(Invalid_Session);
    }

    this.authenticatedUser = authenticatedUser;
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

      // Put the caller where the DAO can see it, and check they may do this.
      // getUser(name) loads the row and parks it in ThreadInfo; a user deleted
      // mid-session fails here, as Invalid_User, which is the right answer.
      //
      // Every command declares the Permission it needs, and this is the one
      // place it is checked.  The DAO methods never have to: a READ-only user
      // is refused every save, delete and create before the method is even
      // resolved, whatever the client chose to draw.
      if (authenticatedUser != null) {
        User caller = dao.getUser(authenticatedUser);
        long needed = command.getName().getRequiredPermission();
        if (needed != 0L && !caller.getPermission().hasPermission(needed)) {
          logger.warn(authenticatedUser + " lacks " + Permission.list.get(needed)
              + " for " + command.getName().name());
          throw new EnvelopeException(Permission_Denied,
              command.getName().name() + " needs " + Permission.list.get(needed) + " permission");
        }
      }

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
      if (dao != null && dao.getTransaction().isActive() && !dao.wasRolledBack()) {
        dao.getTransaction().rollback();
        logger.warn("transaction was rolled back", e);
      }

      //every exception will be caught, logged, and re-thrown to the client.
      logger.fatal("caught exception leaving controller", e);

      // a refusal of our own -- Permission_Denied, Invalid_User -- goes back as is
      if (e instanceof EnvelopeException) throw (EnvelopeException)e;
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
        // The flush and the commit can both throw -- a constraint violation, a
        // stale version -- and this is a finally block, so the catch above cannot
        // see it.  Left unguarded, such a throw skipped close() and disconnect()
        // below and leaked the session: the connection went back to nobody, still
        // holding its row locks, and every later write to those rows blocked for
        // the full lock timeout and then failed the same way.  One duplicate name
        // was enough to start it.
        try {
          if (dao.isActive() && !dao.wasRolledBack()) {
            if (dao.isDirty()) dao.flush();
            dao.commit();
          }
        } catch (Exception e) {
          logger.error("could not commit " + command.getName().name(), e);
          try {
            if (dao.getTransaction().isActive() && !dao.wasRolledBack())
              dao.getTransaction().rollback();
          } catch (Exception rollbackFailed) {
            logger.error("could not roll back either", rollbackFailed);
          }
        }
        // No close() or disconnect() here.  With
        // hibernate.current_session_context_class=thread, ThreadLocalSessionContext
        // has auto-close enabled and its CleanupSync unbinds and closes the
        // session at transaction completion -- so the commit above has already
        // disposed of it.  close() then called getCurrentSession() on an empty
        // ThreadLocal, which OPENS one just to close it, and disconnect() opened a
        // third and left it bound to this Tomcat worker for whichever request
        // landed here next.  Measured with Hibernate statistics: three sessions
        // opened and two closed per request, where one and one is correct.
      }

      // Tomcat reuses its worker threads, so a ThreadLocal left set here would
      // be waiting for whichever request lands on this thread next.
      ThreadInfo.setUser(null);

      logger.info((authenticatedUser != null ? authenticatedUser : "no-session") + SPACE
          + command.getName().name() + SPACE + Interval.span(start, System.currentTimeMillis()));
    }
  }
}