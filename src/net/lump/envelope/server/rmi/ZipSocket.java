package us.lump.envelope.server.rmi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.net.Socket;
import java.net.ServerSocket;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;

/** A Zip Socket Factory. */
class ZipSocket extends Socket implements Serializable {
  private InputStream in;
  private OutputStream out;

  public ZipSocket() { super(); }

  public ZipSocket(String host, int port)
      throws IOException {
    super(host, port);
  }


  public InputStream getInputStream()
      throws IOException {
    if (in == null) {
      in = new ZipInputStream(super.getInputStream());
    }
    return in;
  }

  public OutputStream getOutputStream()
      throws IOException {
    if (out == null) {
      out = new ZipOutputStream(super.getOutputStream());
    }
    return out;
  }

  public synchronized void close() throws IOException {
    ZipOutputStream o = (ZipOutputStream)getOutputStream();
    o.flush();
    super.close();
  }

  public static RMIClientSocketFactory getClientSocketFactory() {
    return new ZipClientSocketFactory();
  }

  public static RMIServerSocketFactory getServerSocketFactory() {
    return new ZipServerSocketFactory();
  }

  static class ZipServerSocketFactory
    implements RMIServerSocketFactory, Serializable {
    public ServerSocket createServerSocket(int port)
        throws IOException {
      return new ZipServerSocket(port);
    }
  }

  public static class ZipServerSocket extends ServerSocket
  {
    ZipSocket socket;

    public ZipServerSocket(int port) throws IOException {
      super(port);
    }

    public Socket accept() throws IOException {
      Socket socket = new ZipSocket();
      implAccept(socket);
      return socket;
    }
  }


  static class ZipClientSocketFactory
      implements RMIClientSocketFactory, Serializable {

    public Socket createSocket(String host, int port) throws IOException {
      return new ZipSocket(host, port);
    }
  }
}