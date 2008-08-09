package us.lump.lib.util;

import us.lump.envelope.client.thread.EnvelopeRunnable;
import us.lump.envelope.client.thread.ThreadPool;

import javax.swing.event.EventListenerList;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.AbstractList;
import java.util.Collection;

/**
 * Makes the downloading of the list returned non-blocking.
 *
 * @author Troy Bowman
 */
public class BackgroundList<E> extends AbstractList<E>
    implements Externalizable {

  transient EventListenerList listenerList = new EventListenerList();
  transient volatile E[] list = (E[])new Object[0];
  transient volatile int size = -1;
  transient volatile int filled = -1;

  public BackgroundList() {}

  public BackgroundList(Collection<E> c) {
    size = c.size();
    list = (E[])c.toArray();
    filled = c.size();

//    E[] array = (E[])java.lang.reflect.Array.
//        newInstance(c.getClass().getComponentType(), size);

  }

  public BackgroundList(E[] array) {
    size = array.length;
    list = array;
    filled = array.length;
  }

  public boolean filled() {
    return size == filled;
  }

  /**
   * Gets the value at <code>index</code>.  If the value doesn't exist yet, it
   * blocks.
   *
   * @param index
   *
   * @return
   */
  public E get(final int index) {
    final Thread t = Thread.currentThread();
    while (filled < size) {

//      addBackgroundListListener(new BackgroundListListener() {
//        public void backgroundListEventOccurred(BackgroundListEvent event) {
//          if (event.getType() == BackgroundListEvent.Type.added
//              && event.getEffect() ==  BackgroundListEvent.Effect.single
//              && event.getRow() >= index) {
//            t.notify();
//          }
//        }
//      });

//      try {
//        t.wait();
//      } catch (InterruptedException e) {
//        if (filled < size) return null;
//      }

      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        if (filled < size) return null;
      }
    }

    return list[index];
  }

  public int size() {
    return size;
  }

  public boolean invalid() {
    return size != list.length;
  }

  public int filledSize() {
    return filled;
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
        BackgroundListEvent.Type.deleted, BackgroundListEvent.Effect.all,
        -1));
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

  public void writeExternal(ObjectOutput out) throws IOException {
    out.writeInt(size);
    for (Object row : list)
      out.writeObject(row);
  }

  public void readExternal(final ObjectInput in)
      throws IOException, ClassNotFoundException {

    ThreadPool.getInstance().execute(new EnvelopeRunnable("Reading") {
      public synchronized void run() {
        try {

          size = in.readInt();
          list = (E[])new Object[size];
          filled = 0;

          for (int i = 0; i < size; i++) {
            list[i] = (E)in.readObject();
            filled = i;
            fireRowAdded(i);
          }
          fireAllFilled();
        } catch (ClassNotFoundException e) {
          e.printStackTrace();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    });
  }
}
