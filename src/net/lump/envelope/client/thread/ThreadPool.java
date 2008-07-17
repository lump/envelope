package us.lump.envelope.client.thread;

import us.lump.envelope.client.ui.StatusBar;

import java.util.concurrent.*;

/**
 * A threadpool that accepts EnvelopeRunnables, which allow the status bar
 * to be updated with what the threadpool is doing.
 *
 * @author Troy Bowman
 * @version $Revision: 1.1 $
 */

public class ThreadPool extends ThreadPoolExecutor {
  private static ThreadPool singleton = null;


  private ThreadPool(int corePoolSize,
                    int maximumPoolSize,
                    long keepAliveTime,
                    TimeUnit unit,
                    SynchronousQueue<Runnable> workQueue) {
    super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
  }

  public static ThreadPool getInstance() {
    if (singleton == null) singleton =
    new ThreadPool(0, Integer.MAX_VALUE,
                   60L, TimeUnit.SECONDS,
                   new SynchronousQueue<Runnable>());
    return singleton;
  }

  protected void beforeExecute(Thread t, java.lang.Runnable r) {
    super.beforeExecute(t, r);

    // add a status bar message
    if (r instanceof EnvelopeRunnable)
      StatusBar.getInstance().addTask(((EnvelopeRunnable)r).getElement());
    else {
      StatusBar.getInstance().addTask(new StatusElement(t));
    }
  }

  protected void afterExecute(java.lang.Runnable r, Throwable t) {
    super.afterExecute(r, t);

    // add a status bar message
    if (r instanceof EnvelopeRunnable)
      StatusBar.getInstance().removeTask(((EnvelopeRunnable)r).getElement());
    else {
      StatusBar.getInstance().removeTask(t);
    }
  }

  public void execute(Runnable command) {
    super.execute(command);
  }

}
