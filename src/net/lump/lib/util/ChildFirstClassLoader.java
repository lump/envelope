package us.lump.lib.util;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * A child-first class loader.
 *
 * @author Troy Bowman
 * @version $Revision: 1.5 $
 */
public class ChildFirstClassLoader extends URLClassLoader {

  JProgressBar progressBar = new JProgressBar(JProgressBar.HORIZONTAL);

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

    System.out.println("ChildFirstClassLoader(" + name + ", " + resolve + ")");

    // First, check if the class has already been loaded
    Class c = findLoadedClass(name);

//    if (c == null && name.matches("^us\\.lump\\..*$")) {
    if (c == null && !name.matches("^java.*$")) {

      String path = name.replace('.', '/').concat(".class");
      URL resource = getResource(path);

      try {
        HttpURLConnection huc = (HttpURLConnection)resource.openConnection();
//          huc.addRequestProperty("accept-encoding","gzip");
        huc.connect();
        int length = huc.getContentLength();
        progressBar.setVisible(true);
        progressBar.setMaximum(length > 0 ? length : 1000);
        progressBar.setMinimum(0);
        progressBar.setName(name);
        InputStream is = huc.getInputStream();
        byte[] buffer = new byte[128];
        byte[] out = new byte[0];
        int read = 0;
        while ((read = is.read(buffer, 0, 128)) > 0) {
          byte[] tmp = new byte[out.length + read];
          System.arraycopy(out, 0, tmp, 0, out.length);
          System.arraycopy(buffer, 0, tmp, out.length, read);
          progressBar.setValue(out.length + read);
          Thread.yield();
          out = tmp;
        }
        progressBar.setVisible(false);
        c = defineClass(name, out, 0, out.length);

        // if we could not find it, delegate to parent

        if (resolve) resolveClass(c);

      } catch (IOException e) {
        throw new ClassNotFoundException(name, e);
      }
    }

    if (c == null) {
      if (getParent() != null) {
        c = getParent().loadClass(name);
      } else {
        c = getSystemClassLoader().loadClass(name);
      }
    }

    return c;
  }

  public JProgressBar getProgressBar() {
    return progressBar;
  }

    /** Override the parent-first resource loading model established by java.lang.Classloader with child-first behavior. */

  public URL getResource(String name) {

    URL url = findResource(name);

    // if we fail, *then* delegate to parent
    if (url == null) url = getParent().getResource(name);

    return url;
  }
}
