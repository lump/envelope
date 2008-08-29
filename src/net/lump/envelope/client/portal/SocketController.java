package us.lump.envelope.client.portal;

import us.lump.envelope.Command;
import us.lump.envelope.client.thread.EnvelopeRunnable;
import us.lump.envelope.client.thread.ThreadPool;
import us.lump.envelope.client.ui.prefs.ServerSettings;
import us.lump.envelope.server.rmi.Controller;
import us.lump.lib.util.BackgroundList;
import us.lump.lib.util.BackgroundListEvent;
import us.lump.lib.util.BackgroundListListener;

import java.io.*;
import java.net.Socket;
import java.rmi.RemoteException;
import java.util.Vector;

/**
 * .
 *
 * @author Troy Bowman
 * @version $Revision: 1.3 $
 */

public class SocketController implements Controller {
  private static final int MAX_READ = 5242880;
  private volatile static Vector<S> socketPool = new Vector<S>();
  private volatile S s;

  private SocketController() {
    s = null;
  }

  // all socketPool edits happen in here...
  public static synchronized SocketController getSocket() throws IOException {
    SocketController sc = new SocketController();

    // remove all stale sockets
    for (S s : socketPool) {
      if (s.busy) continue;
      if (s.socket == null || s.socket.isClosed() || !s.socket.isConnected()
          || s.socket.isInputShutdown() || s.socket.isOutputShutdown()
          || !s.socket.isBound()) {
        socketPool.removeElement(s);
        continue;
      }

      // if socket is valid, busify it and use it.
      if (!s.busy) {
        s.busy = true;
        sc.s = s;
        break;
      }
    }

    // if a socket wasn't found in the pool, make a new one.
    if (sc.s == null) {
      sc.connect();
      socketPool.add(sc.s);
    }

    return sc;
  }

  private synchronized void connect() throws IOException {
    Socket socket = new Socket(
        ServerSettings.getInstance().getHostName(),
        Integer.parseInt(ServerSettings.getInstance().getClassPort()));
    socket.setKeepAlive(true);
    socket.setSoLinger(true, 5000);
    socket.setSoTimeout(0);

    if (s != null) {
      s.busy = true;
      s.socket = socket;
    } else s = new S(socket, true);
  }

  // falsifying busy only happens here.
  public synchronized Serializable invoke(Command... commands)
      throws RemoteException {
    Serializable retval = null;

    try {
      // start the command header
      s.socket.getOutputStream()
          .write("COMMAND /invoke JOTP/1.0\r\n\r\n".getBytes());

      ObjectOutputStream oos =
          new ObjectOutputStream(s.socket.getOutputStream());
      oos.writeObject(commands);
      oos.flush();

      final BufferedInputStream b
          = new BufferedInputStream(s.socket.getInputStream());
      b.mark(MAX_READ);
      int type = b.read();
      switch (type & 0xff) {
        case 0x4f: // O for object
          return (Serializable)new ObjectInputStream(b).readObject();
        case 0x4c: // L for list
          final ObjectInputStream ois = new ObjectInputStream(b);
          final Integer size = (Integer)ois.readObject();
          final BackgroundList bl = new BackgroundList(size);
          ThreadPool.getInstance().execute(new EnvelopeRunnable("Reading") {
            public synchronized void run() {
              try {
                for (int x = 0; x < size; x++) {
                  bl.add(ois.readObject());
                }
              } catch (ClassNotFoundException e) {
                bl.fireAbort();
              } catch (IOException e) {
                bl.fireAbort();
              }
            }
          });
          Thread.sleep(0);
          return bl;
      }
    } catch (IOException e) {
      try {
        s.socket.close();
        s.socket = null;
        connect();
        retval = invoke(commands);
      } catch (IOException e1) {
        throw new RemoteException("invoke failed", e);
      }
    } catch (Exception e) {
      throw new RemoteException("invoke failed", e);
    }

    if (retval instanceof BackgroundList) {
      BackgroundList bl = (BackgroundList)retval;
      bl.addBackgroundListListener(new BackgroundListListener() {
        public void backgroundListEventOccurred(BackgroundListEvent event) {
          if (event.getType().equals(BackgroundListEvent.Type.filled)) {
            s.busy = false;
          }
        }
      });
      if (bl.filled()) s.busy = false;
    } else {
      s.busy = false;
    }

    if (retval instanceof Exception) {
      throw new RemoteException("Remote Exception caught", (Exception)retval);
    } else return retval;
  }

  private class S {
    public Socket socket;
    public boolean busy;

    private S(Socket socket, boolean busy) {
      this.socket = socket;
      this.busy = busy;
    }
  }
}
