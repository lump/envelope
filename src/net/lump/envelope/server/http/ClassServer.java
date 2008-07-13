package us.lump.envelope.server.http;

import java.io.IOException;

/**
 * Create a class server which can serve classes, jars, files...
 *
 * @author Troy Bowman
 * @version $Id: ClassServer.java,v 1.6 2008/07/13 22:52:06 troy Exp $
 */
public class ClassServer extends AbstractServer {

  /**
   * Constructs a ClassServer that listens on <b>port</b> and obtains a class's
   * bytecodes using the method <b>getBytes</b>.
   *
   * @param port the port number
   * @throws java.io.IOException
   */
  public ClassServer(int port) throws IOException {
    super(port, 50, HttpRequestHandler.class, 2000, 5, 10);
  }
}
