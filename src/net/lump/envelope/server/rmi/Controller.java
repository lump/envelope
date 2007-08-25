package us.lump.envelope.server.rmi;

import us.lump.envelope.Command;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Command Controller.
 *
 * @author Troy Bowman
 */
public interface Controller extends Remote {
  /**
   * Invoke a command.
   *
   * @param commands one ore more Command.
   *
   * @return an object which contains either the direct result of the command,
   *         or a list of results if there was more than one command,
   *
   * @throws RemoteException
   */
  public Serializable invoke(Command... commands) throws RemoteException;
}