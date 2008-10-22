package us.lump.lib.util;

import javax.swing.event.EventListenerList;
import java.io.Serializable;
import java.util.AbstractList;
import java.util.Collection;

/**
 * A thread safe list which allows loading of the list in the background along
 * with allowing listeners to register for updates.
 *
 * @author Troy Bowman
 * @version $Id: BackgroundList.java,v 1.11 2008/10/22 01:22:14 troy Test $
 */
@SuppressWarnings({"unchecked"})
public class BackgroundList<E> extends AbstractList<E> implements Serializable {

  private transient EventListenerList listenerList = new EventListenerList();
  private transient volatile E[] list = null;
  private transient volatile int filled = -1;
  private transient volatile boolean abort = false;
  private static final Object token = new Object();

  public BackgroundList() {}

  public BackgroundList(int size) {
    synchronized (token) {
      list = (E[])(new Object[size]);
    }
  }

  public BackgroundList(Collection<E> c) {
    synchronized (token) {
      list = (E[])c.toArray();
      filled = c.size() - 1;
    }
  }

  public BackgroundList(E[] array) {
    synchronized (token) {
      list = array;
      filled = array.length -1;
    }
  }

  public boolean aborted() {
    return abort;
  }

  public boolean filled() {
    return list != null && filled == list.length - 1;
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
      try {
        synchronized (token) {
          if (filled >= index && list.length > index)
            break;
          token.wait(250);
        }
      } catch (InterruptedException e) {
        abort = true;
      }
    } while (!abort);

    return list[index];
  }

  public int size() {
    while (list == null && !abort)
      try {
        synchronized (token) { token.wait(250); }
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
        BackgroundListEvent.Type.aborted,
        BackgroundListEvent.Effect.all,
        filledSize()));
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
        filledSize()));
  }

  public void fireAllRemoved() {
    fireChange(new BackgroundListEvent(
        this,
        BackgroundListEvent.Type.deleted, BackgroundListEvent.Effect.all,
        filledSize()));
  }

  public void fireChange(BackgroundListEvent e) {
    Object[] listeners = listenerList.getListenerList();
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (listeners[i] == BackgroundListListener.class) {
        ((BackgroundListListener)listeners[i + 1])
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
      token.notifyAll();
    }
    fireRowAdded(fire);
    if (fire == (list.length - 1)) fireAllFilled();
    return true;
  }
}