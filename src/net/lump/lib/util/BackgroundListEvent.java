package us.lump.lib.util;

import java.util.EventObject;

/**
 * An event generated by BackgroundList.
 *
 * @author Troy Bowman
 * @version $Id: BackgroundListEvent.java,v 1.4 2008/09/12 00:21:47 troy Exp $
 */

public class BackgroundListEvent extends EventObject {
  public enum Type {
    added, deleted, changed, filled, aborted
  }

  public enum Effect {
    single, all
  }

  private Type type;
  private Effect effect;
  private int row;
  private String message;

  public Type getType() {
    return type;
  }

  public Effect getEffect() {
    return effect;
  }

  public int getRow() {
    return row;
  }

  public String getMessage() {
    return message;
  }

  public BackgroundListEvent(Object source,
                             Type type,
                             Effect effect,
                             int row,
                             String message) {
    super(source);
    this.type = type;
    this.effect = effect;
    this.row = row;
    this.message = message;
  }

  public BackgroundListEvent(Object source,
                             Type type,
                             Effect effect,
                             int row) {
    this(source, type, effect, row, null);
  }

  public BackgroundListEvent(Object source,
                             Type type,
                             Effect effect,
                             String message) {
    this(source, type, effect, -1, message);
  }

  public BackgroundListEvent(Object source,
                             Type type,
                             Effect effect) {
    this(source, type, effect, -1, null);
  }
}
