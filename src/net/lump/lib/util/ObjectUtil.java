package us.lump.lib.util;

import org.apache.log4j.Logger;

import java.io.*;

// Copyright SOS Staffing 2009

/**
 * Misc Object Utilities.
 *
 * @author troy
 * @version $Id: ObjectUtil.java,v 1.2 2009/04/25 00:18:21 troy Exp $
 */
public class ObjectUtil {

  private static Logger logger = Logger.getLogger(ObjectUtil.class);

  /**
   * Deep-copy an object.
   *
   * @param obj to be copied.
   *
   * @return a deep-copied object.
   */
  @SuppressWarnings({"unchecked"}) public static <T> T deepcopy(T obj) {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    T out = null;
    try {
      (new ObjectOutputStream(baos)).writeObject(obj);
      out = (T)(new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))).readObject();
    } catch (IOException e) {
      logger.warn("unexpected I/O exception during deep copy");
      // I don't think we'll have an IO exception with this, ever.
    } catch (ClassNotFoundException e) {
      logger.warn("unexpected ClassNotFoundException during deep copy");
      // Considering this is using generics, the object passed in is an instance
      // of an already defined class, so I don't see how this could get thrown.
    }

    // return copied object or null.
    return out;
  }

}
