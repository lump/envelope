package us.lump.lib.util;

import javax.swing.event.EventListenerList;
import java.io.Serializable;
import java.util.AbstractList;
import java.util.Collection;

/**
 * Makes the downloading of the list returned non-blocking.
 *
 * @author Troy Bowman
 */
public class BackgroundList<E> extends AbstractList<E> implements Serializable {

  private transient EventListenerList listenerList = new EventListenerList();
  private transient volatile E[] list = null;
  private transient volatile int filled = -1;
  private transient volatile boolean abort = false;
  private static final Object token = new Object();

  public BackgroundList() {}

  public BackgroundList(int size) {
    synchronized (token) {
      //noinspection unchecked
      list = (E[])new Object[size];
    }
  }

  public BackgroundList(Collection<E> c) {
    synchronized (token) {
      //noinspection unchecked
      list = (E[])c.toArray();
      filled = c.size();
    }
  }

  public BackgroundList(E[] array) {
    synchronized (token) {
      list = array;
      filled = array.length;
    }
  }

  public boolean aborted() {
    return abort;
  }

  public boolean filled() {
    return list != null && filled == list.length -1;
  }

  /**
   * Gets the value at <code>index</code>. If the value doesn't exist yet, it
   * blocks execution on current thread.
   *
   * @param index
   *
   * @return
   */
  public E get(final int index) {
    do {
      if (filled >= index) break;

      try {
        if (abort) throw new InterruptedException();
        Thread.sleep(1);
      } catch (InterruptedException e) {
        break;
      }
    } while (true);
    return filled < index ? null : list[index];
  }

  public int size() {
    while (list == null && !abort)
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        break;
      }
    return list == null ? -1 : list.length;
  }

  public int filledSize() {
    return filled;
  }

  public void fireAbort() {
    abort = true;
    fireChange(new BackgroundListEvent(
        this,
        BackgroundListEvent.Type.aborted, BackgroundListEvent.Effect.all, -1));
  }

  public void fireRowAdded(int row) {
    fireChange(new BackgroundListEvent(
        this,
        BackgroundListEvent.Type.added, BackgroundListEvent.Effect.single,
        row));
  }

  public void fireRowRemoved(int row) {
    fireChange(new BackgroundListEvent(
        this,
        BackgroundListEvent.Type.deleted, BackgroundListEvent.Effect.single,
        row));
  }

  public void fireRowChanged(int row) {
    fireChange(new BackgroundListEvent(
        this,
        BackgroundListEvent.Type.changed, BackgroundListEvent.Effect.single,
        row));
  }

  public void fireAllFilled() {
    fireChange(new BackgroundListEvent(
        this,
        BackgroundListEvent.Type.filled, BackgroundListEvent.Effect.all,
        -1));
  }

  public void fireAllRemoved() {
    fireChange(new BackgroundListEvent(
        this,
        BackgroundListEvent.Type.deleted, BackgroundListEvent.Effect.all));
  }

  public void fireChange(BackgroundListEvent e) {
    Object[] listeners = listenerList.getListenerList();
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (listeners[i] == BackgroundListListener.class) {
        ((BackgroundListListener)listeners[i+1])
            .backgroundListEventOccurred(e);
      }
    }
  }

  public void addBackgroundListListener(BackgroundListListener l) {
    listenerList.add(BackgroundListListener.class, l);
  }

  public boolean add(E object) {
    int fire = -1;
    synchronized (token) {
      list[++filled] = object;
      fire = filled;
    }
    fireRowAdded(fire);
    if (fire == (list.length - 1)) fireAllFilled();
    return true;
  }
}