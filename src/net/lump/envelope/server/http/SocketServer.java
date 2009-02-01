package us.lump.envelope.server.http;

import java.io.IOException;

/**
 * Create a class server which can serve classes, jars, files...
 *
 * @author Troy Bowman
 * @version $Id: SocketServer.java,v 1.1 2009/02/01 02:33:42 troy Test $
 */
public class SocketServer extends AbstractServer {

  /**
   * Constructs a ClassServer that listens on <b>port</b> and obtains a class's
   * bytecodes using the method <b>getBytes</b>.
   *
   * @param port the port number
   *
   * @throws java.io.IOException
   */
  public SocketServer(int port) throws IOException {
    super(port, 100, SocketRequestHandler.class, 2000, 10, 2000);
  }
}
