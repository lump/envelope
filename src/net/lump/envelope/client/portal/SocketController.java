package us.lump.envelope.client.portal;

import us.lump.envelope.Command;
import us.lump.envelope.client.ui.prefs.ServerSettings;
import us.lump.envelope.server.rmi.Controller;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.rmi.RemoteException;

/**
 * .
 *
 * @author Troy Bowman
 * @version $Revision: 1.1 $
 */

public class SocketController implements Controller {

  Socket s;

  public SocketController() throws IOException {
    s = new Socket(
        ServerSettings.getInstance().getHostName(),
        Integer.parseInt(ServerSettings.getInstance().getClassPort()));
  }

  public Serializable invoke(Command... commands) throws RemoteException {
    Serializable retval = null;

    try {
      s.setKeepAlive(true);
      s.setSoTimeout(0);

      s.getOutputStream().write("COMMAND /invoke HTTP/1.0\r\n\r\n".getBytes());
      ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
      oos.writeObject(commands);
      oos.flush();

      ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
//    oos.close();

      retval = (Serializable)ois.readObject();

      if (retval instanceof Exception) {
        throw new Exception((Exception)retval);
      }
      s.close();
    } catch (Exception e) {
      throw new RemoteException("invoke failed", e);
    }
    return retval;
  }
}
