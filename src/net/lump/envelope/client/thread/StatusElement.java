package us.lump.envelope.client.thread;

/**
 * Status element.
 *
 * @author Troy Bowman
 * @version $Revision: 1.3 $
 */

public class StatusElement {
  private long id;
  Object value;
  private static long count = 0;

  private StatusElement() {}

  public StatusElement(Object value) {
    this.id = getNextId();
    this.value = value;
  }

  public long getId() { return id; }

  public Object getValue() { return value; }

  public void setValue(Object value) {
    this.value = value;
  }

  private synchronized long getNextId() {
    return ++count;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    StatusElement element = (StatusElement)o;

    if (id != element.id) return false;
    return !(value != null
             ? !value.equals(element.value)
             : element.value != null);

  }

  public String toString() {
    return value == null ? "null" : value.toString();
  }

  public int hashCode() {
    int result;
    result = (int)(id ^ (id >>> 32));
    result = 31 * result + (value != null ? value.hashCode() : 0);
    return result;
  }
}