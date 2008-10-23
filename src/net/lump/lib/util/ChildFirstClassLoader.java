package us.lump.lib.util;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * A child-first class loader.
 *
 * @author Troy Bowman
 * @version $Revision: 1.4 $
 */
public class ChildFirstClassLoader extends URLClassLoader {

  public ChildFirstClassLoader(URL[] urls) {
    super(urls);
  }

  public ChildFirstClassLoader(URL[] urls, ClassLoader parent) {
    super(urls, parent);
  }

  public void addURL(URL url) {
    super.addURL(url);
  }

  public Class loadClass(String name) throws ClassNotFoundException {
    return loadClass(name, false);
  }

  /** Overrides the parent-first behavior established by java.lang.Classloader. */
  @Override
  protected Class loadClass(String name, boolean resolve)
      throws ClassNotFoundException {

    //System.out.println("ChildFirstClassLoader("+name+", "+resolve+")");

    // First, check if the class has already been loaded
    Class c = findLoadedClass(name);

    // if not loaded, search the local (child) resources
//    if (c == null && !name.matches("^(?:javax?|sun|com\\.apple)\\..*$")) {
    if (c == null && name.matches("^us\\.lump\\..*$")) {
      try {
        c = findClass(name);
      } catch (ClassNotFoundException cnfe) {
        // ignore
      }
    }

    // if we could not find it, delegate to parent
    if (c == null) {
      if (getParent() != null) {
        c = getParent().loadClass(name);
      } else {
        c = getSystemClassLoader().loadClass(name);
      }
    }

    if (resolve) resolveClass(c);

    return c;
  }

  /**
   * Override the parent-first resource loading model established by
   * java.lang.Classloader with child-first behavior.
   */
  public URL getResource(String name) {

    URL url = findResource(name);

    // if we fail, *then* delegate to parent
    if (url == null) url = getParent().getResource(name);

    return url;
  }
}
