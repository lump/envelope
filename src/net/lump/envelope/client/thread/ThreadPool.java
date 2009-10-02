package net.lump.envelope.client.thread;

import net.lump.envelope.client.ui.components.StatusBar;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A threadpool that accepts EnvelopeRunnables, which allow the status bar
 * to be updated with what the threadpool is doing.
 *
 * @author Troy Bowman
 * @version $Id: ThreadPool.java,v 1.8 2009/10/02 22:06:23 troy Exp $
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
    if (r instanceof StatusRunnable)
      StatusBar.getInstance().addTask(((StatusRunnable)r).getElement());
    else {
      StatusBar.getInstance().addTask(new StatusElement(t));
    }
  }

  protected void afterExecute(java.lang.Runnable r, Throwable t) {
    super.afterExecute(r, t);

    // add a status bar message
    if (r instanceof StatusRunnable)
      StatusBar.getInstance().removeTask(((StatusRunnable)r).getElement());
    else {
      StatusBar.getInstance().removeTask(t);
    }
  }

  public void execute(Runnable command) {
    super.execute(command);
  }

}
