package net.lump.lib.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Compression utility class.
 *
 * @author Troy Bowman
 * @version $Id: Compression.java,v 1.6 2009/10/02 22:06:23 troy Exp $
 */

public class Compression {

  public static ByteArrayOutputStream compress(Object o)
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GZIPOutputStream z = new GZIPOutputStream(baos);
    ObjectOutputStream oos = new ObjectOutputStream(z);
    oos.writeObject(o);
    oos.flush();
    oos.close();
    return baos;
  }

  public static ByteArrayOutputStream compress(ByteArrayOutputStream inBaos)
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GZIPOutputStream z = new GZIPOutputStream(baos);
    inBaos.writeTo(z);
    z.flush();
    z.close();
    return baos;
  }

  public static ByteArrayOutputStream serializeOnly(Object o)
      throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(o);
    oos.flush();
    oos.close();
    return baos;
  }
}
