package net.lump.envelope.client.ui.components.forms.transaction;

import net.lump.envelope.shared.entity.Transaction;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author troy
 * @version $Id$
 */
abstract class Changeable<C extends JComponent, V> {

  int dirtyDelay = 250;
  Timer dirtyTimer = null;


  /**
   * Gives a Runnable that will handle a change event in this scope.
   * @return Runnable
   */
  Runnable getDataChangeHandler(final Transaction transaction) {
    return new Runnable() {
      public void run() {
        if (hasValidInput())
          scheduleDirtyTimer(dirtyDelay, transaction);
      }
    };
  }

  /**
   * Schedules the dirty timer to debounce many update events.
   * @param delay the time that must pass without any updates for the timer to fire.
   */
  void scheduleDirtyTimer(final int delay, final Transaction transaction) {
    if (dirtyTimer != null && dirtyTimer.isRunning())
      dirtyTimer.stop();
    else {
      dirtyTimer = new Timer(delay, new ActionListener(){
        public void actionPerformed(ActionEvent e) {
          updateAction(transaction);
        }
      });
    }
    dirtyTimer.setRepeats(false);
    dirtyTimer.setInitialDelay(delay);
    dirtyTimer.start();
  }


  /**
   * This will sync the form with the data bean and run the update if the input is valid.
   */
  void updateAction(Transaction transaction) {
    if (hasValidInput() && saveState(transaction)) {
      getSaveOrUpdate().run();
    }
  }

  /**
   * Sets the delay to debounce in milliseconds.
   * @param delay
   */
  void setDirtyDelay(int delay) {
    this.dirtyDelay = delay;
  }

  /**
   * Gives the saveOrUpdate Runnable, which will be run when things in the right scope when things are finally ready.
   * @return Runnable
   */
  abstract Runnable getSaveOrUpdate();


  /**
   * Assigns the provided Runnable to the appropriate listeners for change monitoring.
   *
   * @param dataChange
   */
  abstract void addDataChangeListener(Runnable dataChange);

  /**
   * Gives the component.
   * @return
   */
  abstract public JComponent getComponent();

  /**
   * Tests the entry for valid input.  If the input is valid, it is considered ready to send.
   * @return
   */
  abstract public boolean hasValidInput();

  /**
   * Gets the value from the form.
   * @return V
   */
  abstract public V getValue();

  /**
   * Gets the appropriate value from the entity.
   * @return V
   */
  abstract public V getState(Transaction transaction);

  /**
   * Saves the component's state to the provided transaction.
   * @return true if they were different and the save was required.
   */
  abstract public boolean saveState(Transaction transaction);
}
