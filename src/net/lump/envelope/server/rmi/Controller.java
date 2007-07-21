package us.lump.envelope.server.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Command Controller.
 *
 * @author Troy Bowman
 * @revision $Id: Controller.java,v 1.1 2007/07/21 20:15:04 troy Exp $
 */
public interface Controller extends Remote {
  /**
   * Invoke a command.
   *
   * @param commands one ore more Command.
   *
   * @return an object which contains either the direct result of the command, or a list of results if there was more
   *         than one command,
   *
   * @throws RemoteException
   */
  public Object invoke(Command... commands) throws RemoteException;
}