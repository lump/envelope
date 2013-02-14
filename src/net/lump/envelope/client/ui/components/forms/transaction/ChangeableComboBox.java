package net.lump.envelope.client.ui.components.forms.transaction;

import javax.swing.*;

public class ChangeableComboBox extends Changeable<JComboBox, String>{
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

  @Override public String getValue() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override public String getState() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override public boolean saveState() {
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
