package us.lump.lib.util;

import us.lump.envelope.client.thread.ThreadPool;
import us.lump.envelope.client.thread.EnvelopeRunnable;

import java.util.*;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;
import java.io.*;
import java.lang.reflect.Field;

/**
 * Makes the marshalling of the list returned non-blocking.
 *
 * @author Troy Bowman
 */
public class IncrementalArray<S extends Serializable> extends AbstractList
    implements Externalizable {

  transient Serializable[] list = new Serializable[0];
  transient int size = -1;

  public IncrementalArray() {}

  public IncrementalArray(Collection<S> c) {
    Object[] temp = c.toArray();
    size = temp.length;
    list = Arrays.copyOf(temp, size, Serializable[].class);
  }

  public boolean filled() {
    return size == list.length;
  }

  public boolean invalid() {
    return size == -1;
  }

//  public addListener()
//  EventListener


  public Object get(int index) {
    return list[index];
  }

  public int size() {
    return size;
  }

  public void writeExternal(ObjectOutput out) throws IOException {

//    GZIPOutputStream gz = new GZIPOutputStream((OutputStream)out);
//    ObjectOutputStream o = new ObjectOutputStream(gz);
    out.writeInt(size);
    for (Serializable row : list)
      out.writeObject(row);
//    gz.finish();
  }

  public void readExternal(ObjectInput in)
      throws IOException, ClassNotFoundException {

//    GZIPInputStream gz = new GZIPInputStream((InputStream)in);
//    final ObjectInputStream o = new ObjectInputStream(gz);

//    ThreadPool.getInstance().execute(new EnvelopeRunnable("Reading") {
//      public synchronized void run() {
//        try {

          size = in.readInt();
          list = new Serializable[size];

          for (int i = 0; i < size; i++)
            list[i] = (Serializable)in.readObject();
//        } catch (ClassNotFoundException e) {
//          e.printStackTrace();
//        } catch (IOException e) {
//          e.printStackTrace();
//        }
//      }
//    });
  }
}
