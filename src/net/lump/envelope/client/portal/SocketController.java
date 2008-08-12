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
 * @version $Revision: 1.2 $
 */

public class SocketController implements Controller {

  private static Socket s;

  public SocketController() throws IOException {
    init();
  }

  private void init() throws IOException {
    if (s == null || s.isClosed() || !s.isConnected() || s.isInputShutdown()
        || s.isOutputShutdown() || ! s.isBound()) {
      s = new Socket(
          ServerSettings.getInstance().getHostName(),
          Integer.parseInt(ServerSettings.getInstance().getClassPort()));
      s.setKeepAlive(true);
      s.setSoLinger(true, 5000);
      s.setSoTimeout(0);
    }
    s.getOutputStream().write("COMMAND /invoke JOTP/1.0\r\n\r\n".getBytes());
  }

  public Serializable invoke(Command... commands) throws RemoteException {
    Serializable retval = null;

    try {
      ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
      oos.writeObject(commands);
      oos.flush();

      ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
//    oos.close();

      retval = (Serializable)ois.readObject();

      if (retval instanceof Exception) {
        throw new Exception((Exception)retval);
      }
    } catch (IOException e) {
      try {
        s.close();
        s = null;
        init();
        invoke(commands);
      } catch (IOException e1) {
        throw new RemoteException("invoke failed", e);  
      }
    } catch (Exception e) {
      throw new RemoteException("invoke failed", e);
    }
    return retval;
  }
}
