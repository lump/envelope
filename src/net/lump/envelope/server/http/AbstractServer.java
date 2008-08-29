package us.lump.envelope.server.http;

import org.apache.log4j.Logger;

import javax.net.ServerSocketFactory;
import java.net.ServerSocket;
import java.net.SocketException;

/** Abstract super class for creating servers */
public abstract class AbstractServer extends Thread {
  /** Server sock that will listen for incoming connections */
  protected ServerSocket serverSocket;

  /** Boolean that controls whether or not this server is listening */
  protected boolean running;

  /** The port that this server is listening on */
  protected int port;

  /** The number of requests to backlog if we are busy */
  protected int backlog;

  /** A Request Queue used for high throughput servers */
  protected RequestQueue requestQueue;

  private static final Logger logger = Logger.getLogger(ClassServer.class);

  /** Creates a new AbstractServer */
  public AbstractServer(int port,
                        int backlog,
                        Class requestHandler,
                        int maxQueueLength,
                        int minThreads,
                        int maxThreads) {
    // Save our socket parameters
    this.port = port;
    this.backlog = backlog;

    // Create our request queue
    this.requestQueue = new RequestQueue(requestHandler,
                                         maxQueueLength,
                                         minThreads,
                                         maxThreads);
  }

  /** Starts this server */
  public void startServer() {
    try {
      // Create our Server Socket
      ServerSocketFactory ssf = ServerSocketFactory.getDefault();
      serverSocket = ssf.createServerSocket(this.port, this.backlog);
      serverSocket.setSoTimeout(60);

      // Start our thread
      this.start();
    }
    catch (Exception e) {
      logger.error(e);
    }
  }

  /** Stops this server */
  public void stopServer() {
    try {
      this.running = false;
      this.serverSocket.close();
    }
    catch (Exception e) {
      logger.error(e);
    }
  }

  /** Body of the server: listens in a tight loop for incoming requests */
  public void run() {
    // Start the server
    logger.info("Server Started, listening on port: " + this.port);
    this.running = true;
    while (running) {
      try {
        // Accept the next connection
        // Add the socket to the new RequestQueue
        this.requestQueue.add(serverSocket.accept());
      }
      catch (SocketException se) {
        // We are closing the ServerSocket in order to shutdown the server, so if
        // we are not currently running then ignore the exception.
        if (this.running) {
          logger.error(se);
        }
      }
      catch (Exception e) {
        logger.error(e);
      }
    }
    logger.info("Shutting down...");

    // Shutdown our request queue
    this.requestQueue.shutdown();
  }
}