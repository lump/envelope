package us.lump.envelope.client;

import us.lump.envelope.Command;
import us.lump.envelope.client.thread.EnvelopeRunnable;
import us.lump.envelope.client.thread.ThreadPool;
import us.lump.envelope.client.ui.components.StatusBar;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.client.ui.prefs.ServerSettings;
import us.lump.envelope.server.XferFlags;
import static us.lump.envelope.server.XferFlags.Flag.*;
import us.lump.envelope.server.rmi.Controller;
import us.lump.lib.util.BackgroundList;
import us.lump.lib.util.Base64;
import us.lump.lib.util.Compression;
import us.lump.lib.util.Encryption;

import javax.crypto.SecretKey;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Vector;
import java.util.zip.GZIPInputStream;

/**
 * A class which manages connections from the client, and invokes commands
 * through said connections..
 *
 * @author Troy Bowman
 * @version $Id: SocketClient.java,v 1.5 2008/09/17 05:54:19 troy Exp $
 */

public class SocketClient implements Controller {
  private static final int MAX_READ = 5242880;
  private volatile static Vector<S> socketPool = new Vector<S>();
  private volatile S s;

  private static final ServerSettings serverSettings
      = ServerSettings.getInstance();

  private SocketClient() {
    s = null;
  }

  // all socketPool edits happen in here...
  public static synchronized SocketClient getSocket() throws IOException {
    SocketClient sc = new SocketClient();

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
        try {
          InputStream i = sc.s.socket.getInputStream();
          int available = i.available();
          if (available > 0) {
            byte[] buffer = new byte[available];
            i.read(buffer, 0, available);
          }
        }
        catch (SocketException e) {
          s.socket.close();
          s.socket = null;
          sc.connect();
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
        serverSettings.getHostName(),
        Integer.parseInt(serverSettings.getClassPort()));
    socket.setKeepAlive(true);
    socket.setSoLinger(false, 0);
//    socket.setSoTimeout(10000);
    socket.setSoTimeout(0);

    if (s != null) {
      s.busy = true;
      s.socket = socket;
    } else s = new S(socket, true);
  }

  // falsifying busy only happens here.
  public synchronized Serializable invoke(Command command)
      throws RemoteException {
    Serializable retval = null;

    try {
      // start the command header
      s.socket.getOutputStream()
          .write("COMMAND /invoke JOTP/1.0\r\n\r\n".getBytes());

      ByteArrayOutputStream baos = Compression.serializeOnly(command);
      OutputStream os = s.socket.getOutputStream();

      // new empty transfer flags
      XferFlags flags = new XferFlags();

      // if we're compressing, compress the stream
      if (serverSettings.getCompress()) {
        flags.add(F_COMPRESS);
        baos = Compression.compress(baos);
      }

      // if we're encrypting, encrypt the (possibly compressed) stream
      SecretKey sessionKey = null;
      String encodedKey = null;
      if (serverSettings.getEncrypt()
          && command.getName() != Command.Name.authChallengeResponse
          && command.getName() != Command.Name.getServerPublicKey) {
        // turn on encrypted flag
        flags.add(F_ENCRYPT);
        // generate a new session key
        sessionKey = Encryption.generateSymKey();
        // encode the key with server's public key for transfer and base64 it.
        encodedKey =
            Base64.byteArrayToBase64(
                Encryption.wrapSecretKey(
                    sessionKey, LoginSettings.getInstance().getServerKey()));
      }

      // write our flags
      os.write(flags.getByte());
      // write the (possibly mangled) command

      // if we're encrypted...
      if (flags.has(F_ENCRYPT)
          && sessionKey != null
          && encodedKey != null) {
        ObjectOutputStream oos = new ObjectOutputStream(os);
        oos.writeObject(encodedKey);
        oos.flush();
        oos.writeObject(Encryption.encodeSym(sessionKey, baos.toByteArray()));
        oos.flush();
      }
      // not encrypted, nothing special, just write it.
      else {
        baos.writeTo(os);
      }
      os.flush();

      // prepare inputstream
      final BufferedInputStream b
          = new BufferedInputStream(s.socket.getInputStream());

      XferFlags flag = new XferFlags((byte)b.read());
      b.mark(MAX_READ);

      InputStream i = b;

      // if we're encrypted, wrap the InputStream in a CipherInputStream
      if (flag.has(F_ENCRYPT))
        i = Encryption.decodeSym(sessionKey, i);

      // if we're compressed, wrap the InputStream ina GZIPInputstream
      if (flag.has(F_COMPRESS)) i = new GZIPInputStream(i);

      // finally, create an objectInputStream of the possibly compressed
      // and possibly encrypted stream.
      final ObjectInputStream ois = new ObjectInputStream(i);

      // if object retunred is there, read an object first
      if (flag.has(F_OBJECT_RETURNED)) {
        retval = (Serializable)ois.readObject();
      }

      // if list_returned is there, read a list, possibly after already having
      // read an object...
      if (flag.has(F_LIST_RETURNED)) {
        final Integer size = (Integer)ois.readObject();
        final BackgroundList<Serializable> bl
            = new BackgroundList<Serializable>(size);
        ThreadPool.getInstance().execute(
            new EnvelopeRunnable(Strings.get("reading")) {
              public synchronized void run() {
                try {
                  for (int x = 0; x < size; x++) {
                    this.setStatusMessage(Strings.get("reading") + " " + x);
                    StatusBar.getInstance().updateLabel();
                    bl.add((Serializable)ois.readObject());
                    if (bl.aborted()) break;
                  }
                } catch (ClassNotFoundException e) {
                  bl.fireAbort();
                } catch (IOException e) {
                  bl.fireAbort();
                }
                s.busy = false;
              }
            });

        // if retval is already defined, return a list with the bl inside
        if (retval == null) retval = bl;
        else retval = (Serializable)Arrays.asList(retval, bl);
      }
    } catch (IOException e) {
      try {
        s.socket.close();
        s.socket = null;
        connect();
        retval = invoke(command);
      } catch (IOException e1) {
        throw new RemoteException("invoke failed", e);
      }
    } catch (Exception e) {
      throw new RemoteException("invoke failed", e);
    }

    s.busy = false;
    if (retval instanceof Exception) {
      throw new RemoteException("Remote Exception caught", (Exception)retval);
    }
    else return retval;
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
