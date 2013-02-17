package net.lump.envelope.client.ui.components.forms.transaction;

import net.lump.envelope.client.ui.MainFrame;
import net.lump.envelope.client.ui.components.forms.table_query_bar.TableQueryBar;
import net.lump.envelope.client.ui.defs.Strings;

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

  public void handleDataChange() {
    TransactionForm form = MainFrame.getInstance().getTransactionForm();
    if (hasValidInput()) {
      form.setSaveStateLabel(Strings.get("save.pending"));
      scheduleDirtyTimer(dirtyDelay);
    }
    else {
      form.setSaveStateLabel(Strings.get("waiting.for.valid.state"));
    }
  }

  /**
   * Schedules the dirty timer to debounce many update events.
   * @param delay the time that must pass without any updates for the timer to fire.
   */
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

  /**
   * This will sync the form with the data bean and run the update if the input is valid.
   */
  void updateAction() {
    if (hasValidInput())
      if (saveState())
        getSaveOrUpdate().run();
      else
        MainFrame.getInstance().getTransactionForm().setSaveStateLabel(null);
  }

  /**
   * Sets the delay to debounce in milliseconds.
   * @param delay
   */
  void setDirtyDelay(int delay) {
    this.dirtyDelay = delay;
  }

  /**
   * Assigns the provided Runnable to the appropriate listeners for change monitoring.
   */
  abstract void addDataChangeListener();

  /**
   * Removes the data change listner.
   */
  abstract void removeDataChangeListener();

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
  abstract public V getState();

  /**
   * Saves the component's state to the provided transaction.
   * @return true if they were different and the save was required.
   */
  abstract public boolean saveState();

  /**
   * Fill this method in with that which saves this entity to the database;
   */
  abstract public Runnable getSaveOrUpdate();

}
