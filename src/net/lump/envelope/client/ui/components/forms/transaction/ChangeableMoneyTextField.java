package net.lump.envelope.client.ui.components.forms.transaction;

import net.lump.envelope.client.ui.components.MoneyTextField;
import net.lump.lib.Money;

import javax.swing.*;

public class ChangeableMoneyTextField extends Changeable<MoneyTextField, Money> {
  @Override Runnable getSaveOrUpdate() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override void addDataChangeListener(Runnable dataChange) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override public JComponent getComponent() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override public boolean hasValidInput() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override public Money getValue() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override public Money getState() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override public boolean saveState() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
