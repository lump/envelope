package us.lump.envelope.client.ui;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Holder for a cached threadpool.
 *
 * @author Troy Bowman
 * @version $Revision: 1.1 $
 */

public class ThreadPool {
  private static ThreadPool singleton = null;
  private ThreadPoolExecutor service;

  private ThreadPool() {
    service = (ThreadPoolExecutor)Executors.newCachedThreadPool();
  }

  public static ThreadPool getInstance() {
    if (singleton == null) singleton = new ThreadPool();
    return singleton;
  }

  public ThreadPoolExecutor getService() {
    return service;
  }

  public void execute(Runnable command) {
    service.execute(command);
  }

}
