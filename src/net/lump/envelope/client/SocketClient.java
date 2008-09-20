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
import java.math.BigInteger;
import java.net.Socket;
import java.net.SocketException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.zip.GZIPInputStream;

/**
 * A class which manages connections from the client, and invokes commands
 * through said connections..
 *
 * @author Troy Bowman
 * @version $Id: SocketClient.java,v 1.7 2008/09/20 06:08:31 troy Exp $
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


  public Serializable invoke(Command command) throws RemoteException {
    return invoke(Arrays.asList(command));
  }

  public synchronized Serializable invoke(List<Command> commands)
      throws RemoteException {

    if (commands.size() == 0)
      throw new
          IllegalArgumentException("list must contain one or more commands");

    Serializable retval = null;

    try {
      // start the command header
      s.socket.getOutputStream().write(
          "COMMAND /invoke JOTP/1.0\r\n\r\n".getBytes());

      // new empty transfer flags
      XferFlags outFlags = new XferFlags();

      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      // depending on list size, set flags and prepare object stream
      if (commands.size() == 1) {
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        outFlags.add(F_OBJECT);
        oos.writeObject(commands.get(0));
        oos.close();
      }
      else {
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        outFlags.add(F_LIST);
        oos.writeObject(new Integer(commands.size()));
        for (Command c : commands) {
          // if the command contains any unEncryptables, puke
          if (Command.Name.unEncryptables().and(c.getName().bit()).compareTo(
              BigInteger.ZERO) != 0)
            throw new IllegalArgumentException(
                c.getName() + "can't be used in multiple list of commands");
          oos.writeObject(c);
        }
        oos.close();
      }

      OutputStream os = s.socket.getOutputStream();

      // if we're compressing, compress the command(s) object stream
      if (serverSettings.getCompress()) {
        outFlags.add(F_COMPRESS);
        baos = Compression.compress(baos);
      }

      SecretKey sessionKey = null;
      String encodedKey = null;
      // if we're encrypting, encrypt the (possibly compressed) stream
      // except for the unencryptable commands
      if (serverSettings.getEncrypt() && (Command.Name.unEncryptables().and(
          commands.get(0).getName().bit()).compareTo(BigInteger.ZERO) == 0)) {
        // turn on encrypted flag
        outFlags.add(F_ENCRYPT);
        // generate a new session key
        sessionKey = Encryption.generateSymKey();
        // encode the key with server's public key for transfer and base64 it.
        encodedKey =
            Base64.byteArrayToBase64(
                Encryption.wrapSecretKey(
                    sessionKey, LoginSettings.getInstance().getServerKey()));
      }

      // write the xfer flags
      os.write(outFlags.getByte());

      // write the (possibly mangled) command
      // if we're encrypted...
      if (outFlags.has(F_ENCRYPT) && sessionKey != null && encodedKey != null) {
        ObjectOutputStream oos = new ObjectOutputStream(os);
        // first write our generated key
        oos.writeObject(encodedKey);
        oos.flush();
        // then write our encrypted command(s)
        oos.writeObject(Encryption.encodeSym(sessionKey, baos.toByteArray()));
        oos.flush();
      }
      // not encrypted, nothing special, just write it.
      else {
        baos.writeTo(os);
      }
      os.flush();

      // prepare inputstream
      final BufferedInputStream b =
          new BufferedInputStream(s.socket.getInputStream());

      final XferFlags inFlag = new XferFlags((byte)b.read());
      b.mark(MAX_READ);

      InputStream i = b;

      // if we're encrypted, wrap the InputStream in a CipherInputStream
      if (inFlag.has(F_ENCRYPT)) i = Encryption.decodeSym(sessionKey, i);

      // if we're compressed, wrap the InputStream ina GZIPInputstream
      if (inFlag.has(F_COMPRESS)) i = new GZIPInputStream(i);

      // finally, create an objectInputStream of the possibly compressed
      // and possibly encrypted stream.
      final ObjectInputStream ois = new ObjectInputStream(i);

      // if object retunred is there, read an object first
      if (inFlag.has(F_OBJECT)) retval = (Serializable)ois.readObject();

      // if list_returned is there, read a list, possibly after already having
      // read an object...
      if (inFlag.hasAny(F_LIST, F_LISTS)) {
        final Integer size = (Integer)ois.readObject();
        final BackgroundList<Serializable> bl
            = new BackgroundList<Serializable>(size);
        ThreadPool.getInstance().execute(
            new EnvelopeRunnable(Strings.get("reading")) {
              public synchronized void run() {
                try {
                  for (int x = 0; x < size; x++) {
                    if (inFlag.has(F_LISTS)) {
                      Integer subSize = (Integer)ois.readObject();

                      // if size is -1397705797, that's code for the size being
                      // only one, and not being a list with one entry.
                      // since size should never be negative, we're safe.
                      // (1397705797 is "SOLE" in integer form :)
                      if (size == -1397705797) {
                        this.setStatusMessage(
                            Strings.get("reading") + " " + (x+1));
                        StatusBar.getInstance().updateLabel();
                        bl.add((Serializable)ois.readObject());
                      }
                      else {
                        BackgroundList<Serializable> subBl = 
                            new BackgroundList<Serializable>(subSize);
                        // add the new sub-backgroundlist now, so that the
                        // listeners will be notified, and they'll see that it
                        // is a background list, and they can register
                        // listeners for the sub list if they want.
                        bl.add(subBl);

                        // now that the listeners have been notified, each sub-
                        // sequent add to the child list will probably be
                        // noticed by the listeners who register.
                        for (int y = 0; y < subSize; y++) {
                          this.setStatusMessage(
                              Strings.get("reading") + " " + (x+1) + ":" + y);
                          StatusBar.getInstance().updateLabel();
                          subBl.add((Serializable)ois.readObject());
                        }
                      }
                    }
                    else {
                      this.setStatusMessage(Strings.get("reading") + " " + x);
                      StatusBar.getInstance().updateLabel();
                      bl.add((Serializable)ois.readObject());
                      if (bl.aborted()) break;
                    }
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
        // throw it away
        s.socket.close();
        s.socket = null;
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
