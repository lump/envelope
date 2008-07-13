package us.lump.envelope.server.http;

/**
 * Created by IntelliJ IDEA. User: troy Date: Jul 13, 2008 Time: 2:00:39 PM To
 * change this template use File | Settings | File Templates.
 */

import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.BlockingQueue;

/**
 * A Request Queue accepts new requests and processes them with its associated
 * thread pool
 */
public class RequestQueue {
  /** Request queue */
  private LinkedList queue = new LinkedList();

  /** The maximum length that the queue can grow to */
  private int maxQueueLength;

  /** The minimum number of threads in this queue?s associated thread pool */
  private int minThreads;

  /**
   * The maximum number of threads that can be in this queue?s associated
   * thread pool
   */
  private int maxThreads;

  /** The current number of threads */
  private int currentThreads = 0;

  /** The name of the request handler implementation class */
  private Class requestHandler;

  /** The thread pool that is servicing this request */
  private List threadPool = new ArrayList();

  private static final Logger logger = Logger.getLogger(RequestQueue.class);

  private boolean running = true;

  /** Creates a new RequestQueue */
  public RequestQueue(Class requestHandler,
                      int maxQueueLength,
                      int minThreads,
                      int maxThreads) {
    // Initialize our parameters
    this.requestHandler = requestHandler;
    this.maxQueueLength = maxQueueLength;
    this.minThreads = minThreads;
    this.maxThreads = maxThreads;
    this.currentThreads = this.minThreads;

    // Create the minimum number of threads
    for (int i = 0; i < this.minThreads; i++) {
      RequestThread thread =
          new RequestThread(this, i, requestHandler);
      thread.start();
      this.threadPool.add(thread);
    }
  }

  /** Returns the name of the RequestHandler implementation class */
  public Class getRequestHandler() {
    return this.requestHandler;
  }

  /**
   * Adds a new object to the end of the queue
   *
   * @param o Adds the specified object to the Request Queue
   */
  public synchronized void add(Object o) throws RuntimeException {
    // Validate that we have room of the object before we add it to the queue
    if (queue.size() > this.maxQueueLength) {
      throw new RuntimeException("The Request Queue is full. Max size = "
                                 + this
          .maxQueueLength);
    }

    // Add the new object to the end of the queue
    queue.addLast(o);

    // See if we have an available thread to process the request
    boolean availableThread = false;
    for (Iterator i = this.threadPool.iterator(); i.hasNext();) {
      RequestThread rt = (RequestThread)i.next();
      if (!rt.isProcessing()) {
        logger.debug("Found an available thread");
        availableThread = true;
        break;
      }
      logger.error("Thread is busy");
    }

    // See if we have an available thread
    if (!availableThread) {
      if (this.currentThreads < this.maxThreads) {
        logger.debug("Creating a new thread to satisfy the incoming request");
        RequestThread thread = new RequestThread(this,
                                                 currentThreads++,
                                                 this.requestHandler);
        thread.start();
        this.threadPool.add(thread);
      } else {
        logger.debug("Whoops, can?t grow the thread pool, guess you have to wait");
      }
    }

    // Wake someone up
    notifyAll();
  }

  /** Returns the first object in the queue */
  public synchronized Object getNextObject() {
    // Setup waiting on the Request Queue
    while (queue.isEmpty()) {
      try {
        if (!running) {
          // Exit criteria for stopping threads
          return null;
        }
        wait();
      }
      catch (InterruptedException ie) {}
    }

    // Return the item at the head of the queue
    return queue.removeFirst();
  }

  /** Shuts down the request queue and kills all of the request threads */
  public synchronized void shutdown() {
    logger.debug("Shutting down request threads...");

    // Mark the queue as not running so that we will free up our request threads
    this.running = false;

    // Tell each thread to kill itself
    for (Iterator i = this.threadPool.iterator(); i.hasNext();) {
      RequestThread rt = (RequestThread)i.next();
      rt.killThread();
    }

    // Wake up all threads and let them die
    notifyAll();
  }
}