package us.lump.envelope.client.ui.components;

import us.lump.envelope.client.thread.StatusElement;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.client.ui.images.ImageResource;

import javax.swing.*;
import java.util.Vector;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.*;

/**
 * This keeps track of things that should be displayed on the status bar..
 *
 * @author Troy Bowman
 * @version $Revision: 1.1 $
 */

public class StatusBar extends JLabel {

  private Vector<StatusElement> tasks
      = new Vector<StatusElement>();

  private long lastRun = 0;
  private Timer timer;

  private static StatusBar singleton = null;
  private ImageIcon busy = new ImageIcon(ImageResource.class.getResource("busy.gif"));
  private ImageIcon idle = new ImageIcon(ImageResource.class.getResource("idle.gif"));

  {
    setIcon(idle);
  }

  public static StatusBar getInstance() {
    if (singleton == null) singleton = new StatusBar();
    singleton.repaint();
    return singleton;
  }

  private StatusBar() {
    super();
    setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
//    setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
    setHorizontalAlignment(SwingConstants.LEFT);
    setVerticalAlignment(SwingConstants.CENTER);
    setFont(getFont().deriveFont(Font.PLAIN));
  }

  public StatusElement addTask(String description) {
    StatusElement e = new StatusElement(description);
    return addTask(e);
  }

  public StatusElement addTask(StatusElement e) {
    changeTask(true, e);
    return e;
  }

  public void removeTask(StatusElement e) {
    changeTask(false, e);
  }

  public void removeTask(Object o) {
    changeTask(false, o);
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
    repaint();
  }

  public synchronized void repaint() {
    long now = System.currentTimeMillis();
    if (getIcon() != null && (now - 100) > lastRun) {
      if (timer != null) timer.stop();

      if (tasks == null) tasks = new Vector<StatusElement>();
      String line = "[" + tasks.size() + "] ";
      if (getIcon() == null) setIcon(idle);
      if (tasks.size() == 0) {
        if (!getIcon().equals(idle)) {
          busy.setImageObserver(null);
          setIcon(idle);
        }
        line += Strings.get("ready");
      } else {
        if (!getIcon().equals(busy)) {
          setIcon(busy);
          busy.setImageObserver(this);
        }
        for (StatusElement task : tasks)
          line += task.getId() + ". " + task.toString() + "  ";
      }

      this.setText(line);
      lastRun = System.currentTimeMillis();
    }
    else {
      timer = new Timer(
          (int)(100 - (now - lastRun)),
          new ActionListener() {
            public void actionPerformed(ActionEvent e) {
              repaint();
            }
          });
      timer.setRepeats(false);
      timer.start();
    }
    super.repaint();
  }
}
