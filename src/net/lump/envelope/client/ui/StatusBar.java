package us.lump.envelope.client.ui;

import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.client.thread.StatusElement;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import java.util.Vector;

/**
 * This keeps track of things that should be displayed on the status bar..
 *
 * @author Troy Bowman
 * @version $Revision: 1.3 $
 */

public class StatusBar extends JLabel {

  private Vector<StatusElement> tasks
      = new Vector<StatusElement>();

  private static StatusBar singleton = null;


  public static StatusBar getInstance() {
    if (singleton == null) singleton = new StatusBar();
    singleton.repaint();
    return singleton;
  }

  private StatusBar() {
    super();
    setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
    setVerticalAlignment(SwingConstants.CENTER);
  }

  public StatusElement addTask(String description) {
    StatusElement e = new StatusElement(description);
    return addTask(e);
  }

  public StatusElement addTask(StatusElement e) {
    changeTask(true, e);
    repaint();
    return e;
  }

  public void removeTask(StatusElement e) {
    changeTask(false, e);
    repaint();
  }
  public void removeTask(Object o) {
    changeTask(false, o);
    repaint();
  }

  private synchronized void changeTask(boolean addRemove, Object o) {
    StatusElement e = null;
    if (o instanceof StatusElement) e = (StatusElement)o;
    else for (StatusElement task : tasks)
      if (task.getValue().equals(e)) { e = task; break; }

    if (e != null) {
      if (addRemove) tasks.add(e);
      else tasks.remove(e);
    }
  }


  public synchronized void repaint() {
    if (tasks == null) tasks = new Vector<StatusElement>();
    String line = "[" + tasks.size() + "] ";
    if (tasks.size() == 0) line += Strings.get("ready");
    else for (StatusElement task : tasks) {
      line += "(" + task.toString() + ") ";
    }
    this.setText(line);
    super.repaint();
  }

}
