package us.lump.envelope.server.rmi;

import us.lump.envelope.entity.Account;
import us.lump.envelope.entity.Category;

import java.io.Serializable;

/**
 * A parameter.
 *
 * @author Troy Bowman
 * @version $Revision: 1.1 $
 */
public enum Param {
  public_key(String.class),
  user_name(String.class),
  challenge_response(String.class),

  year(Integer.class),

  account(Account.class),
  category(Category.class),
  reconciled(Boolean.class)
      ;



  private final Class type;

  Param(Class<? extends Serializable> type) {
    this.type = type;
  }

  /**
   * Get the class type of the Param.
   *
   * @return Class
   */
  public Class getType() {
    return this.type;
  }
}
