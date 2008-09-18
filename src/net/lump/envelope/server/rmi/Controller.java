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
   * @param command to invoke
   *
   * @return an object which contains either the direct result of the command,
   *
   * @throws RemoteException on failure
   */
  public Serializable invoke(Command command) throws RemoteException;
}