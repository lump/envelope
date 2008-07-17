package us.lump.envelope.client.ui;

import us.lump.envelope.client.ui.defs.Strings;

import javax.swing.*;
import java.util.Vector;

/**
 * This keeps track of things that should be displayed on the status bar..
 *
 * @author Troy Bowman
 * @version $Revision: 1.1 $
 */

public class StatusBar extends JLabel {

  private Vector<Element<String>> tasks = new Vector<Element<String>>();
  private static StatusBar singleton = null;


  public static StatusBar getInstance() {
    if (singleton == null) singleton = new StatusBar();
    singleton.repaint();
    return singleton;
  }

  private StatusBar() {
    super();
  }

  public Element addTask(String description) {
    Element<String> e = new Element<String>(description);
    tasks.add(e);
    repaint();
    return e;
  }

  public void removeTask(Element e) {
    tasks.remove(e);
    repaint();
  }

  public void repaint() {
    if (tasks == null) tasks = new Vector<Element<String>>();
    String line = "(" + tasks.size() + ") ";
    if (tasks.size() == 0) line += Strings.get("ready");
    else for (Element task : tasks) {
      line += "[" + task + "] ";
    }
    this.setText(line);
    super.repaint();
  }

  public static class Element<T> {
    private long id;
    T value;
    private static long count = 0;

    private Element() {}

    public Element(T value) {
      this.id = getNextId();
      this.value = value;
    }

    public long getId() { return id; }

    public T getValue() { return value; }

    private synchronized long getNextId() {
      return ++count;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Element element = (Element)o;

      if (id != element.id) return false;
      return !(value != null
               ? !value.equals(element.value)
               : element.value != null);

    }

    public String toString() {
      return new StringBuilder().append(value)
          .append("-")
          .append(id)
          .toString();
    }

    public int hashCode() {
      int result;
      result = (int)(id ^ (id >>> 32));
      result = 31 * result + (value != null ? value.hashCode() : 0);
      return result;
    }
  }
}
