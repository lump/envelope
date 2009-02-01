package us.lump.envelope.server.http;

import org.apache.log4j.Logger;

import java.net.Socket;

/** A Request thread handles incoming requests */
public class RequestThread extends Thread {
  /** A reference to our request queue */
  private RequestQueue queue;

  /** Our state: are we running or not? */
  private boolean running;

  /** Our processing state: are we currently processing a request? */
  private boolean processing = false;

  /** Our thread number, used for accounting purposes */
  private int threadNumber;

  /** Our request handler */
  private RequestHandler requestHandler;

  private static final Logger logger = Logger.getLogger(RequestThread.class);


  /**
   * @param queue        The queue that we are associated with
   * @param threadNumber Our thread number
   * @param handler      The class which will handle incoming requests.
   */
  public RequestThread(RequestQueue queue,
                       int threadNumber,
                       Class handler) {
    this.queue = queue;
    this.threadNumber = threadNumber;
    try {
      this.requestHandler = (RequestHandler)handler.newInstance();
    } catch (InstantiationException e) {
      logger.error("Could not instantiate " + handler.getName(), e);
    } catch (IllegalAccessException e) {
      logger.error("Could not access " + handler.getName(), e);
    }
  }

  /**
   * Returns true if we are currently processing a request, false otherwise.
   * @return boolean
   */
  public boolean isProcessing() {
    return this.processing;
  }

  /** If a thread is waiting, then wake it up and tell it to die */
  public void killThread() {
    logger.debug("[" + threadNumber + "]: Attempting to kill thread...");
    this.running = false;
  }

  /** The thread's main processing loop */
  public void run() {
    this.running = true;
    while (running) {
      try {
        // Obtain the next pending socket from the queue; only process requests if
        // we are still running. The shutdown mechanism will wake up our threads at this
        // point, so our state could have changed to not running here.
        Socket socket = queue.getNext();
        if (running) {
          // Mark ourselves as processing a request
          this.processing = true;
          logger.debug("[" + threadNumber + "]: Processing request...");

          // Handle the request
          this.requestHandler.handleRequest(socket);

          // We've finished processing, so make ourselves available for the next request
          this.processing = false;
          logger.debug("["
                       + threadNumber
                       + "]: Finished Processing request...");
        }
      }
      catch (Exception e) {
        logger.error(e);
      }
    }

    logger.info("[" + threadNumber + "]: Thread shutting down...");
  }
}