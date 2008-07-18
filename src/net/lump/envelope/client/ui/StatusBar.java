package us.lump.envelope.client.ui;

import us.lump.envelope.client.thread.StatusElement;
import us.lump.envelope.client.ui.defs.Strings;

import javax.swing.*;
import java.util.Vector;

/**
 * This keeps track of things that should be displayed on the status bar..
 *
 * @author Troy Bowman
 * @version $Revision: 1.7 $
 */

public class StatusBar extends JLabel {

  private Vector<StatusElement> tasks
      = new Vector<StatusElement>();

  private static StatusBar singleton = null;
  private static Icon busy;
  private static Icon idle;


  public static StatusBar getInstance() {
    if (singleton == null) singleton = new StatusBar();
    singleton.repaint();
    return singleton;
  }

  private StatusBar() {
    super();

    busy = new ImageIcon(this.getClass().getResource("images/busy.gif"));
    idle = new ImageIcon(this.getClass().getResource("images/idle.gif"));

    setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
//    setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
    setHorizontalAlignment(SwingConstants.LEFT);
    setVerticalAlignment(SwingConstants.CENTER);
    setIcon(idle);
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
      if (task.getValue().equals(e)) {
        e = task;
        break;
      }

    if (e != null) {
      if (addRemove) tasks.add(e);
      else tasks.remove(e);
    }
  }


  public synchronized void repaint() {
    if (tasks == null) tasks = new Vector<StatusElement>();
    String line = "[" + tasks.size() + "] ";
    if (tasks.size() == 0) {
      if (getIcon().equals(busy)) setIcon(idle);
      line += Strings.get("ready");
    } else {
      if (getIcon().equals(idle)) setIcon(busy);
      for (StatusElement task : tasks)
        line += "(" + task.toString() + ") ";
    }

    this.setText(line);
    super.repaint();
  }

}
