package us.lump.envelope.client.portal;

import us.lump.envelope.Command;
import us.lump.envelope.client.thread.EnvelopeRunnable;
import us.lump.envelope.client.thread.ThreadPool;
import us.lump.envelope.client.ui.prefs.ServerSettings;
import us.lump.envelope.server.rmi.Controller;
import us.lump.lib.util.BackgroundList;

import java.io.*;
import java.net.Socket;
import java.rmi.RemoteException;
import java.util.Vector;

/**
 * .
 *
 * @author Troy Bowman
 * @version $Revision: 1.8 $
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

        // flush the input stream if there's anything on it.
        InputStream i = sc.s.socket.getInputStream();
        int available = i.available();
        if (available > 0) {
          byte[] buffer = new byte[available];
          int read = i.read(buffer, 0, available);
          String message = new String(buffer).substring(0, read);

          // if the message is an HTTP message, close socket
          if (message.matches("HTTP/\\d+\\.\\d+\\s+")) {
            s.socket.close();
            socketPool.removeElement(s);
            continue;
          }
        }
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
    socket.setSoLinger(false, 0);
    socket.setSoTimeout(10000);

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
          retval = (Serializable)new ObjectInputStream(b).readObject();
          break;
        case 0x4c: // L for list
          final ObjectInputStream ois = new ObjectInputStream(b);
          final Integer size = (Integer)ois.readObject();
          final BackgroundList bl = new BackgroundList(size);
          ThreadPool.getInstance().execute(new EnvelopeRunnable("Reading") {
            public synchronized void run() {
              try {
                for (int x = 0; x < size; x++) {
                  bl.add(ois.readObject());
//                  if (bl.aborted()) break;
                }
              } catch (ClassNotFoundException e) {
                bl.fireAbort();
              } catch (IOException e) {
                bl.fireAbort();
              }
              s.busy = false;
            }
          });
          Thread.sleep(0);
          return bl;
        case 0x48: // H -- http error
          b.reset();
          byte[] buffer = new byte[2048];
          int length = b.read(buffer, 0, 2048);
          System.err.println(new String(buffer).substring(0, length));
//          s.socket.close();
          return null;
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

    s.busy = false;
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
