package net.lump.envelope.shared.command;

import org.hibernate.criterion.DetachedCriteria;
import net.lump.envelope.shared.entity.Identifiable;

// so the command declarations read as plain words; an enum cannot alias these
// itself, since its constants initialize before any static field of its own
import static net.lump.envelope.shared.command.security.Permission.ADMIN;
import static net.lump.envelope.shared.command.security.Permission.READ;
import static net.lump.envelope.shared.command.security.Permission.WRITE;
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
   */
  public enum Name {

    // ---- no session: the login exchange, and pings ------------------------
    ping(false, Dao.Security),
    // the readiness probe's command: reads the latest transaction's day back
    // and checks it, so it proves the database, the schema and the date
    // handling, where ping proves only that the server answers
    ready(false, Dao.Security),
    getChallenge(false, Dao.Security, String.class, PublicKey.class),
    authChallengeResponse(false, Dao.Security, String.class, byte[].class, PublicKey.class),
    getServerPublicKey(false, Dao.Security),

    // ---- READ: looking -------------------------------------------------------
    authedPing(Dao.Security, READ),
    detachedCriteriaQueryList(Dao.Generic, READ, DetachedCriteria.class, Boolean.class),
    detachedCriteriaQueryUnique(Dao.Generic, READ, DetachedCriteria.class, Boolean.class),
    get(Dao.Generic, READ, Class.class, Serializable.class),
    load(Dao.Generic, READ, Class.class, Serializable.class),

    // ---- WRITE: changing money and the shape it is filed into --------------
    save(Dao.Generic, WRITE, Identifiable.class),
    saveOrUpdate(Dao.Generic, WRITE, Identifiable.class),
//    merge(Dao.Generic, Identifiable.class),
//    refresh(Dao.Generic, Identifiable.class),

    updateReconciled(Dao.Action, WRITE, Integer.class, Boolean.class),
    deleteAllocation(Dao.Action, WRITE, Integer.class),
    createTransaction(Dao.Action, WRITE, Integer.class, Date.class, String.class, String.class, Money.class),
    deleteTransaction(Dao.Action, WRITE, Integer.class),

    createAccount(Dao.Action, WRITE, Integer.class, String.class, String.class),
    renameAccount(Dao.Action, WRITE, Integer.class, String.class),
    deleteAccount(Dao.Action, WRITE, Integer.class),
    createCategory(Dao.Action, WRITE, Integer.class, String.class),
    renameCategory(Dao.Action, WRITE, Integer.class, String.class),
    deleteCategory(Dao.Action, WRITE, Integer.class),
    applyAllocationPreset(Dao.Action, WRITE, Integer.class, String.class, Money.class),
    deletePresetRow(Dao.Action, WRITE, Integer.class),
    deletePresetNamed(Dao.Action, WRITE, Integer.class, String.class),
    renamePresetNamed(Dao.Action, WRITE, Integer.class, String.class, String.class),

    // ---- ADMIN: users and budgets -------------------------------------------
    renameBudget(Dao.Action, ADMIN, Integer.class, String.class),
    // whoAmI and setPassword only need a session: anyone may ask who they are
    // and set their own password.  setPassword checks self-or-ADMIN itself.
    whoAmI(Dao.Action, READ),
    listUsers(Dao.Action, ADMIN),
    listBudgets(Dao.Action, ADMIN),
    createUser(Dao.Action, ADMIN, String.class, String.class, Integer.class, Long.class, String.class),
    updateUser(Dao.Action, ADMIN, Integer.class, String.class, Integer.class, Long.class),
    setPassword(Dao.Action, READ, Integer.class, String.class),
    createBudget(Dao.Action, ADMIN, String.class),
    deleteBudget(Dao.Action, ADMIN, Integer.class),

    // NOTE: bit() is derived from ordinal(), so add new commands at the END --
    // inserting one in the middle renumbers every command after it.
    //more command definitions here...
    ;

    private final BigInteger bit;
    private final Dao dao;
    private final ArrayList<Class> params = new ArrayList<Class>();
    private final Boolean sessionRequired;
    private final long requiredPermission;

    /**
     * A command that needs no session -- and so can carry no permission, there
     * being nobody to hold one.  Only the login exchange and the pings.
     */
    Name(boolean sessionRequired, Dao dao, Class... params) {
      bit = BigInteger.ZERO.setBit(ordinal());
      this.params.addAll(Arrays.asList(params));
      this.dao = dao;
      this.sessionRequired = sessionRequired;
      this.requiredPermission = 0L;
    }

    /**
     * A command that needs a session, and this permission on it.  Controller
     * checks it once, at dispatch, against the user the session belongs to; the
     * DAO methods never have to.  There is deliberately no form of this that
     * lets the permission be left off: a command nobody thought about does not
     * compile, rather than quietly running for everyone.
     */
    Name(Dao dao, long requiredPermission, Class... params) {
      bit = BigInteger.ZERO.setBit(ordinal());
      this.params.addAll(Arrays.asList(params));
      this.dao = dao;
      this.sessionRequired = true;
      this.requiredPermission = requiredPermission;
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
     * The Permission bits a caller must hold for this command; 0 for one that
     * needs no session.
     *
     * @return long
     */
    public long getRequiredPermission() {
      return requiredPermission;
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