package net.lump.envelope.client.ui.components.forms.entries;

import net.lump.envelope.shared.entity.Identifiable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author troy
 * @version $Id$
 */
abstract class FormEntry<C extends JComponent, E extends Identifiable, V> {

  int dirtyDelay = 250;
  Timer dirtyTimer = null;


  Runnable getDataChangeHandler() {
    return new Runnable() {
      public void run() {
        if (hasValidInput())
          scheduleDirtyTimer(dirtyDelay);
      }
    };
  }

  void scheduleDirtyTimer(final int delay) {
    if (dirtyTimer != null && dirtyTimer.isRunning())
      dirtyTimer.stop();
    else {
      dirtyTimer = new Timer(delay, new ActionListener(){
        public void actionPerformed(ActionEvent e) {
          updateAction();
        }
      });
    }
    dirtyTimer.setRepeats(false);
    dirtyTimer.setInitialDelay(delay);
    dirtyTimer.start();
  }

  void updateAction() {
    if (hasValidInput() && saveState()) {
      getSaveOrUpdate().run();
    }
  }

  void setDirtyDelay(int delay) {
    this.dirtyDelay = delay;
  }

  abstract Runnable getSaveOrUpdate();
  abstract void addDataChangeListener(Runnable dataChange);

  abstract public boolean hasValidInput();
  abstract public C getComponent();
  abstract public E getEntity();
  abstract public V getValue();
  abstract public V getState();

  /**
   * Saves the component's state to the entity
   * @return true if they were different and the save was required.
   */
  abstract public boolean saveState();
}
