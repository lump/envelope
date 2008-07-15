package us.lump.envelope.server.http;

import java.io.IOException;

/**
 * Create a class server which can serve classes, jars, files...
 *
 * @author Troy Bowman
 * @version $Id: ClassServer.java,v 1.7 2008/07/15 17:14:59 troy Exp $
 */
public class ClassServer extends AbstractServer {

  /**
   * Constructs a ClassServer that listens on <b>port</b> and obtains a class's
   * bytecodes using the method <b>getBytes</b>.
   *
   * @param port the port number
   *
   * @throws java.io.IOException
   */
  public ClassServer(int port) throws IOException {
    super(port, 100, HttpRequestHandler.class, 2000, 10, 20);
  }
}
