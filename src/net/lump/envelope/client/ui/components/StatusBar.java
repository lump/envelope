package us.lump.envelope.client.ui.components;

import us.lump.envelope.client.thread.StatusElement;
import us.lump.envelope.client.ui.defs.Strings;

import javax.swing.*;
import java.awt.*;
import java.util.Vector;

/**
 * This keeps track of things that should be displayed on the status bar..
 *
 * @author Troy Bowman
 * @version $Revision: 1.6 $
 */

public class StatusBar extends JPanel {

  private static StatusBar singleton = null;
  Spinner spinner = new Spinner(20, 20);
  //  Spinner spinner = new Spinner(256, 256);
  StatusLabel label = new StatusLabel();
  JProgressBar progress = new JProgressBar(JProgressBar.HORIZONTAL, 0, 0);

  public static StatusBar getInstance() {
    if (singleton == null) singleton = new StatusBar();
    singleton.repaint();
    return singleton;
  }

  private StatusBar() {
    setLayout(new BorderLayout());
    this.setLayout(new BorderLayout());
    this.add(spinner, BorderLayout.WEST);
    this.add(label, BorderLayout.CENTER);
    this.add(progress, BorderLayout.EAST);
    progress.setVisible(false);
  }

  public StatusElement addTask(String description) {
    return label.addTask(description);
  }

  public StatusElement addTask(StatusElement e) {
    return label.addTask(e);
  }

  public void removeTask(StatusElement e) {
    label.removeTask(e);
  }

  public void removeTask(Object o) {
    label.removeTask(o);
  }

  public JProgressBar getProgress() {
    return progress;
  }

  public void updateLabel() {
    label.updateLabel();
  }

  class StatusLabel extends JLabel {

    private Vector<StatusElement> tasks = new Vector<StatusElement>();

    private StatusLabel() {
      super();
      setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
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
      if (tasks == null) tasks = new Vector<StatusElement>();

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

      updateLabel();
    }

    public synchronized void updateLabel() {

      String line = "[" + tasks.size() + "] ";
      if (tasks.size() > 0) {
        spinner.startSpinning();
        for (StatusElement task : tasks)
          line += task.getId() + ". " + task.toString() + "  ";
      } else {
        line += Strings.get("ready");
        spinner.stopSpinning();
      }

      this.setText(line);
      repaint();
    }
  }
}
