package net.lump.envelope.client;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author troy
 * @version $Id: MonitorInputStream.java,v 1.1 2010/09/20 23:18:23 troy Exp $
 */
public class MonitorInputStream extends FilterInputStream {
  InputStream in;
  private boolean cumulative = false;
  private long bytesRead = 0;
  private BytesReadListener listener;

  public interface BytesReadListener {
    public void bytesRead(long bytesRead);
  }


  public MonitorInputStream(InputStream in, BytesReadListener listener) {
    super(in);
    this.in = in;
    this.listener = listener;
  }

  public MonitorInputStream(InputStream in, boolean cumulative, BytesReadListener listener) {
    this(in, listener);
    setCumulative(cumulative);
  }

  /**
   * @param cumulative whether or not the events should contain the cumulative count of bytes read.
   */
  public void setCumulative(boolean cumulative) {
    this.cumulative = cumulative;
  }

  @Override public int read() throws IOException {
    int c = in.read();
    fireRead(++bytesRead);
    return c;
  }

  @Override public int read(byte[] b) throws IOException {
    int nr = in.read(b);
    fireRead(bytesRead += nr);
    return nr;
  }

  @Override public int read(byte[] b, int off, int len) throws IOException {
    int nr = in.read(b, off, len);
    fireRead(bytesRead += nr);
    return nr;
  }

  @Override public long skip(long n) throws IOException {
    long nr = in.skip(n);
    fireRead(bytesRead += nr);
    return nr;
  }

  @Override public void close() throws IOException {
    super.close();
    fireRead(bytesRead);
  }

  private void fireRead(long read) {
    listener.bytesRead(read);
    if (!cumulative) bytesRead = 0;
  }
}
