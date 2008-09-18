package us.lump.envelope;

import org.hibernate.criterion.DetachedCriteria;
import us.lump.envelope.entity.Identifiable;
import us.lump.envelope.server.security.Credentials;
import us.lump.lib.util.Encryption;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.*;
import java.util.ArrayList;
import java.util.List;


/**
 * A command.
 *
 * @author Troy Bowman
 * @version $Id: Command.java,v 1.17 2008/09/18 05:48:38 troy Exp $
 */
public class Command implements Serializable {
  /**
   * An application facet.
   *
   * @author Troy Bowman
   */
  public enum Dao {
    Generic,
    Security,
    Action,
  }

  /**
   * A command name.
   *
   * @author Troy Bowman
   * @version $Id: Command.java,v 1.17 2008/09/18 05:48:38 troy Exp $
   */
  public enum Name {

    // security
    ping(false, Dao.Security),
    authedPing(Dao.Security),
    getChallenge(false, Dao.Security, String.class, PublicKey.class),
    authChallengeResponse(false, Dao.Security, String.class, byte[].class),
    getServerPublicKey(false, Dao.Security),

    // generic
    detachedCriteriaQuery(Dao.Generic, DetachedCriteria.class),
    get(Dao.Generic, Class.class, Serializable.class),
    save(Dao.Generic, Identifiable.class),
    saveOrUpdate(Dao.Generic, Identifiable.class),
//    merge(Dao.Generic, Identifiable.class),
//    refresh(Dao.Generic, Identifiable.class),

    // transaction
    updateReconciled(Dao.Action, Integer.class, Boolean.class),

    //more command definitions here...
    ;

    private final BigInteger bit;
    private final Dao dao;
    private final ArrayList<Class> params = new ArrayList<Class>();
    private final Boolean sessionRequired;

    Name(boolean sessionRequired, Dao dao, Class... params) {
      bit =  BigInteger.ZERO.setBit(ordinal());
      for (Class p : params) this.params.add(p);
      this.dao = dao;
      this.sessionRequired = sessionRequired;
    }

    Name(Dao dao, Class... params) {
      this(true, dao, params);
    }

    /**
     * This is the facet of the command.  It refers directly to the class of DAO
     * that will be called.
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
     * Returns a bigInteger with the bits set which are commands which
     * can't be encrypted.
     * @return
     */
    public static BigInteger unEncryptables() {
      return authChallengeResponse.bit()
          .or(getServerPublicKey.bit())
//          .or(....bit());
          ;
    }
  }

  private final Name name;

  // the actual parameters
  private final ArrayList<Serializable> params = new ArrayList<Serializable>();

  // credentials for the session
  private Credentials credentials = null;

  /**
   * A new command.
   *
   * @param name which is an enum that is statically defined.
   */
  public Command(Name name) {
    this.name = name;
  }

  public Command(Name name, Serializable... params) {
    this(name);
    for (Serializable s : params) set(s);
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
   * Returns a Credentials object of the Credentials.
   *
   * @return Credentials
   */
  public Credentials getCredentials() {
    return credentials;
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
      if (value == null
          || this.name.getParamType(id).isInstance(value))
        if (this.params.size() == id) this.params.add(value);
        else if (this.params.size() > id) this.params.set(id, value);
        else
          throw new IllegalStateException("arguments must be added in order");
      else
        throw new IllegalArgumentException(
            this.name.name()
            + " parameter number "
            + id
            + " requires type "
            + this.name.getParamType(id).getSimpleName()
            + " and is not type "
            + value.getClass().getSimpleName());
    } else
      throw new IllegalArgumentException("invalid parameter " + id);
    return this;
  }

  /**
   * Signs the current state of the command with a private key.
   *
   * @param username the username of the signer.
   * @param key      the private key of the signer
   *
   * @return Command the instance of command (for chained method calls)
   *
   * @throws NoSuchAlgorithmException NoSuchAlgorithmException
   * @throws SignatureException       SignatureException
   * @throws InvalidKeyException      InvalidKeyException
   */
  public Command sign(String username, PrivateKey key)
      throws NoSuchAlgorithmException, SignatureException,
      InvalidKeyException, UnsupportedEncodingException {
    this.credentials = new Credentials(username);
    credentials.setSignature(
        Encryption.sign(key,
                        credentials.getUsername() +
                        String.valueOf(credentials.getStamp())));
    return this;
  }

  /**
   * Verifies the signature of the command with a public key.
   *
   * @param key the public key
   *
   * @return boolean whether the signature verifies.
   *
   * @throws NoSuchAlgorithmException
   * @throws SignatureException
   * @throws UnsupportedEncodingException
   * @throws InvalidKeyException
   */
  public boolean verify
      (PublicKey
          key) throws NoSuchAlgorithmException,
      SignatureException, UnsupportedEncodingException,
      InvalidKeyException {
    return Encryption.verify(
        key,
        credentials.getUsername() + String.valueOf(credentials.getStamp()),
        credentials.getSignature()
    );
  }

  public String toString() {
    String out = "";
    if (credentials != null) {
      out += "[" + credentials.getUsername() + "] ";
    }
    out += name.toString() + "(";
    for (Object param : params) out += param.toString() + ",";
    out += ")";
    return out;
  }

  /**
   * Enum's hashcode does not compute the hashcode on the ordinal, and it is
   * marked as final, so it can't be overridden.  Ugh, that's just yucky.
   * <p/>
   * So, here's a hashcode that uses the ordinal, a well as doing a hash on all
   * of the attributes of this Command enum, too.
   *
   * @return int
   */
  public int hashCode() {
    // the value of this ordinal value
    int result = 13 * name.ordinal() + 113;
    // the hash of whether session is required
    result += 13 * name.isSessionRequired().hashCode();

    // step through each param type, hash the class name
    // and hash the corresponding value
    for (int x = 0; x < name.getParamTypes().size(); x++) {
      result += name.getParamType(x).getName().hashCode();
      result += params.get(x) != null ? params.get(x).hashCode() : 0;
    }
    return result;
  }
}