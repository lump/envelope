package net.lump.envelope.shared.command;

import org.hibernate.criterion.DetachedCriteria;
import net.lump.envelope.shared.entity.Identifiable;
import net.lump.lib.Money;

import javax.swing.event.EventListenerList;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.sql.Date;
import java.math.BigInteger;
import java.security.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * A command.  Commands are used by the client to be able to issue remote requests.
 *
 * @author Troy Bowman
 * @version $Id: Command.java,v 1.2 2009/10/02 22:06:23 troy Exp $
 */
public class Command implements Serializable {

  /**
   * Authentication material travels in HTTP headers rather than inside the
   * serialized command.  That buys two things: the signature can cover a digest
   * of the exact bytes on the wire, instead of re-serializing a deserialized
   * object graph and hoping it comes out byte-identical; and the server can check
   * it before handing anything to readObject.
   */
  public static final String H_SESSION = "X-Envelope-Session";
  public static final String H_STAMP = "X-Envelope-Stamp";
  public static final String H_SIGNATURE = "X-Envelope-Signature";
  public static final String H_COMMAND = "X-Envelope-Command";

  /**
   * The exact string that gets signed.  Shared so the two ends cannot drift.
   *
   * @param sessionId the session identifier
   * @param stamp     the client's timestamp
   * @param digest    digest of the serialized command being sent
   *
   * @return String the payload to sign, or to verify against
   */
  public static String signaturePayload(String sessionId, long stamp, String digest) {
    return sessionId + ":" + stamp + ":" + digest;
  }

  /**
   * The DAO that the command is destined for. This is for design-related separation on server side.
   *
   * @author Troy Bowman
   */
  public enum Dao {
    Generic,
    Security,
    Action, }

  /**
   * A Name enumerates the commands, each enumeration defines the name, arguments, facet, whether a session is required for a
   * command, and each command has a bit associated with it for bitwise operations.
   *
   * @author Troy Bowman
   * @version $Id: Command.java,v 1.2 2009/10/02 22:06:23 troy Exp $
   */
  public enum Name {

    // security
    ping(false, Dao.Security),
    authedPing(Dao.Security),
    getChallenge(false, Dao.Security, String.class, PublicKey.class),
    authChallengeResponse(false, Dao.Security, String.class, byte[].class, PublicKey.class),
    getServerPublicKey(false, Dao.Security),

    // generic
    detachedCriteriaQueryList(Dao.Generic, DetachedCriteria.class, Boolean.class),
    detachedCriteriaQueryUnique(Dao.Generic, DetachedCriteria.class, Boolean.class),
    get(Dao.Generic, Class.class, Serializable.class),
    load(Dao.Generic, Class.class, Serializable.class),
    save(Dao.Generic, Identifiable.class),
    saveOrUpdate(Dao.Generic, Identifiable.class),
//    merge(Dao.Generic, Identifiable.class),
//    refresh(Dao.Generic, Identifiable.class),

    // transaction
    updateReconciled(Dao.Action, Integer.class, Boolean.class),
    deleteAllocation(Dao.Action, Integer.class),
    createTransaction(Dao.Action, Integer.class, Date.class, String.class, String.class, Money.class),
    deleteTransaction(Dao.Action, Integer.class),

    // budget structure
    createAccount(Dao.Action, Integer.class, String.class, String.class),
    renameAccount(Dao.Action, Integer.class, String.class),
    deleteAccount(Dao.Action, Integer.class),
    createCategory(Dao.Action, Integer.class, String.class),
    renameCategory(Dao.Action, Integer.class, String.class),
    deleteCategory(Dao.Action, Integer.class),
    applyAllocationPreset(Dao.Action, Integer.class, String.class, Money.class),
    deletePresetRow(Dao.Action, Integer.class),
    deletePresetNamed(Dao.Action, Integer.class, String.class),
    renamePresetNamed(Dao.Action, Integer.class, String.class, String.class),
    renameBudget(Dao.Action, Integer.class, String.class),

    // NOTE: bit() is derived from ordinal(), so add new commands at the END --
    // inserting one in the middle renumbers every command after it.
    //more command definitions here...
    ;

    private final BigInteger bit;
    private final Dao dao;
    private final ArrayList<Class> params = new ArrayList<Class>();
    private final Boolean sessionRequired;

    Name(boolean sessionRequired, Dao dao, Class... params) {
      bit = BigInteger.ZERO.setBit(ordinal());
      this.params.addAll(Arrays.asList(params));
      this.dao = dao;
      this.sessionRequired = sessionRequired;
    }

    private Name(Dao dao, Class... params) {
      this(true, dao, params);
    }

    /**
     * This is the facet of the command.  It refers directly to the class of DAO that will be called.
     *
     * @return Facet
     */
    public Dao getFacet() {
      return dao;
    }

    /**
     * Get the list of params defined for this Name.
     *
     * @return the list of parameters
     */
    public List<Class> getParamTypes() {
      return params;
    }

    /**
     * Get the class of the parameter for the provided index
     *
     * @param i the index
     *
     * @return Class
     */
    public Class getParamType(int i) {
      return params.get(i);
    }

    /**
     * Whether this command requires a session be established.
     *
     * @return Boolean
     */
    public Boolean isSessionRequired() {
      return sessionRequired;
    }

    /**
     * Returns the unique bit, for use in bitwise comparisons.
     *
     * @return BigInteger
     */
    public BigInteger bit() {
      return bit;
    }

    /**
     * Returns a BigInteger with the bits set which are commands which can't be encrypted.
     *
     * @return BigInteger
     */
    public static BigInteger unEncryptables() {
      return getServerPublicKey.bit()
//          .or(authChallengeResponse.bit())
//          .or(....bit());
          ;
    }
  }

  private final Name name;
  private static volatile long counter = 0L;
  private long seqId;
  // the listener is transient because we don't send the listener to the server.
  private transient EventListenerList listenerList = new EventListenerList();

  // the actual parameters
  private final ArrayList<Serializable> params = new ArrayList<Serializable>();

  /**
   * A new command.  Each command requires at least one listener.
   *
   * @param name     the Name.
   * @param listener the listener which will receive output.
   */
  public Command(Name name, OutputListener listener) {
    this.seqId = nextId();
    this.name = name;
    addOutputListener(listener);
  }

  /**
   * A new command whith parameters. Each command requires at least one listener.
   *
   * @param name     the Name.
   * @param listener the Listener which will receive output.
   * @param params   the parameters.
   */
  public Command(Name name, OutputListener listener, Serializable... params) {
    this(name, listener);
    for (Serializable s : params) set(s);
  }

  /**
   * A syncrhonized method for incrementing counter and returning id;
   *
   * @return Long
   */
  private synchronized long nextId() {
    return ++counter;
  }


  /**
   * Get the sequence id for this instance.
   *
   * @return long
   */
  public long getSeqId() {
    return seqId;
  }

  /**
   * Returns the Name enum.
   *
   * @return the enum.
   */
  public Name getName() {
    return name;
  }

  /**
   * Fires an output event to the listeners for this command.
   *
   * @param event The event to fire.
   */
  public void fireOutput(OutputEvent event) {
    Object[] listeners = listenerList.getListenerList();
    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (listeners[i] == OutputListener.class) {
        ((OutputListener)listeners[i + 1]).commandOutputOccurred(event);
      }
    }
  }

  public void addOutputListener(OutputListener listener) {
    if (listener != null) this.listenerList.add(OutputListener.class, listener);
  }

  /**
   * Returns a HashMap of command parameters.
   *
   * @return HashMap
   */
  public List<Serializable> getParams() {
    return params;
  }

  /**
   * Returns the value of the parameter defined by p
   *
   * @param i the Param id
   *
   * @return Serializable value
   */
  public Serializable getParam(int i) {
    return params.get(i);
  }

  /**
   * Set the next unset parameter with provided value.
   *
   * @param value the value
   *
   * @return Command
   */
  public Command set(Serializable value) {
    return set(this.params.size(), value);
  }

  /**
   * Sets a parameter's value.
   *
   * @param id    the Param id
   * @param value the Serializable value
   *
   * @return Command the instance of command (for chained method calls)
   */
  public Command set(int id, Serializable value) {
    // if this command will fit in the list...
    if (this.name.getParamTypes().size() > id) {
      // and the provided parameter is an instance of the parameter's type
      if (value == null || this.name.getParamType(id).isInstance(value)) if (this.params.size() == id) this.params.add(value);
      else if (this.params.size() > id) this.params.set(id, value);
      else throw new IllegalStateException("arguments must be added in order");
      else throw new IllegalArgumentException(
          this.name.name() + " parameter number " + id + " requires type " + this.name.getParamType(id).getSimpleName()
              + " and is not type " + value.getClass().getSimpleName());
    }
    else throw new IllegalArgumentException("invalid parameter " + id);
    return this;
  }

  public String toString() {
    String out = "";
    out += name.toString() + "(";
    for (Object param : params) out += param.toString() + ",";
    out += ")";
    return out;
  }

  /**
   * Enum's hashcode does not compute the hashcode on the ordinal, and it is marked as final, so it can't be overridden.  Ugh,
   * that's just yucky.
   * <p/>
   * So, here's a hashcode that uses the ordinal, as well as doing a hash on all of the attributes of this Command enum, too.
   *
   * @return int
   */
  public int hashCode() {
    // the value of this ordinal value
    int result = 13 * name.ordinal() + 113;
    // the hash of whether session is required
    result += 13 * name.isSessionRequired().hashCode();
    // the hash of the id
    result += 13 * seqId;

    // step through each param type, hash the class name
    // and hash the corresponding value
    for (int x = 0; x < name.getParamTypes().size(); x++) {
      result += name.getParamType(x).getName().hashCode();
      result += params.get(x) != null ? params.get(x).hashCode() : 0;
    }
    return result;
  }


}