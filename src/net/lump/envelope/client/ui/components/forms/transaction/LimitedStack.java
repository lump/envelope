package net.lump.envelope.client.ui.components.forms.transaction;

import java.util.Stack;

/**
 * @author troy
 * @version $Id$
 */
public class LimitedStack<E> extends Stack {
  int stackLimit = 10;

  public LimitedStack(int size) {
    super();
  }

  public LimitedStack() {
    super();
  }

  @Override public Object push(Object item) {
    while (this.size() > stackLimit)
      this.remove(0);

    return super.push(item);
  }
}
