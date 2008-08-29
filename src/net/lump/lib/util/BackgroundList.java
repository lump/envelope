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
  private transient volatile E[] list = (E[])new Object[0];
  private transient volatile int size = -1;
  private transient volatile int filled = -1;
  private transient volatile boolean abort = false;
  private static final Object token = new Object();

  public BackgroundList() {}

  public BackgroundList(int size) {
    synchronized (token) {
      this.size = size;
      list = (E[])new Object[size];
    }
  }

  public BackgroundList(Collection<E> c) {
    synchronized (token) {
      size = c.size();
      list = (E[])c.toArray();
      filled = c.size();
    }
  }

  public BackgroundList(E[] array) {
    synchronized (token) {
      size = array.length;
      list = array;
      filled = array.length;
    }
  }

  public boolean filled() {
    return size != -1 && size == filled;
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
        if (filled >= index) break;
        if (abort) throw new InterruptedException();
        Thread.sleep(50);
      } catch (InterruptedException e) {
        break;
      }
    } while (true);
    return filled < index ? null : list[index];
  }

  public int size() {
    do {
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        break;
      }
    } while (size < 0);
    return size;
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
        ((BackgroundListListener)listeners[i
                                           + 1]).backgroundListEventOccurred(e);
      }
    }
  }

  public void addBackgroundListListener(BackgroundListListener l) {
    listenerList.add(BackgroundListListener.class, l);
  }

  public boolean add(E object) {
    changeList(true, object);
    return true;
  }

//  public void setSize(int size) {
//    synchronized (token) {
//      E[] tempList = (E[])new Object[size];
//      System.arraycopy(
//          list,0,tempList,0,
//          tempList.length >= list.length ? list.length : tempList.length);
//      this.list = tempList;
//      this.size = size;
//    }
//  }

  private void changeList(Boolean add, E object) {
    int fire = -1;
    synchronized (token) {
      if (add) {
        list[++filled] = object;
        fire = filled;
      }
    }
    fireRowAdded(fire);
    if (fire == (size - 1)) fireAllFilled();
  }

//  public void writeExternal(ObjectOutput out) throws IOException {
//    out.writeObject(size);
//    out.flush();
//    for (Object row : list) {
//      out.writeObject(row);
//      out.flush();
//    }
//  }

//  public void readExternal(final ObjectInput in)
//      throws IOException, ClassNotFoundException {
//
//    // use array to allow modification of final boolean
//    final boolean[] done = new boolean[1]; done[0] = false;
//
//    ThreadPool.getInstance().execute(new EnvelopeRunnable("Reading") {
//      public synchronized void run() {
//        try {
//          ObjectInputStream ois = new ObjectInputStream((ObjectInputStream)in);
//          size = (Integer)ois.readObject();
//          list = (E[])new Object[size];
//          filled = -1;
//
//          for (int i = 0; i < size; i++) {
//            list[i] = (E)ois.readObject();
//            filled = i;
//            fireRowAdded(i);
//          }
//          fireAllFilled();
//        } catch (ClassNotFoundException e) {
//          fireAbort();
//        } catch (IOException e) {
//          fireAbort();
//        } finally {
//          done[0] = true;
//        }
//      }
//    });
//
//  }
}
