package net.lump.envelope.shared.entity;


import java.io.Serializable;

/**
 * Entity objects which contain an identification field.
 *
 * @author Troy Bowman
 */
public abstract class Identifiable <T extends Serializable, S extends Serializable>
    implements Serializable, Stampable<S> {

  /*
   * Every mapped entity declares these itself, annotated with its @Id and
   * @Version.  The defaults that used to sit here looked the values up through
   * Hibernate's ClassMetadata -- by way of the server's DAO, from a class the
   * client loads too -- and ClassMetadata went with Hibernate 6.
   */

  /** @return this row's identifier, null until it is saved */
  public abstract T getId();

  public abstract void setId(T id);

  /** @return the version stamp, which a save must present unchanged */
  public abstract S getStamp();
}
