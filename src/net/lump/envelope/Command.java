package us.lump.envelope;

import us.lump.envelope.entity.Account;
import us.lump.envelope.entity.Category;
import us.lump.envelope.server.security.Credentials;
import us.lump.lib.util.Encryption;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.security.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * A command.
 *
 * @author Troy Bowman
 * @version $Revision: 1.5 $
 */
public class Command implements Serializable {
  /**
   * An application facet.
   *
   * @author Troy Bowman
   * @version $Revision: 1.5 $
   */
  public enum Dao {
    Security,
    Action,
    Status,
    Report
  }

  /**
   * A parameter.
   *
   * @author Troy Bowman
   * @version $Revision: 1.5 $
   */
  public enum Param {
    public_key(PublicKey.class),
    user_name(String.class),
    challenge_response(byte[].class),

    year(Integer.class),

    account(Account.class),
    category(Category.class),
    reconciled(Boolean.class),

    //more parameter definitions here...
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


  /**
   * A command name.
   *
   * @author Troy Bowman
   * @version $Revision: 1.5 $
   */
  public enum Name {

    // diag
    ping(false, Dao.Action),
    authedPing(Dao.Action),

    // security
    getChallenge(false, Dao.Security, Param.user_name, Param.public_key),
    authChallengeResponse(false,
                          Dao.Security,
                          Param.user_name,
                          Param.challenge_response),

    // transaction
    listTransactions(Dao.Action, Param.year),

    // report
    getCategoryBalance(Dao.Status,
                       Param.category,
                       Param.year,
                       Param.reconciled),
    getCategoryBalances(Dao.Status, Param.year, Param.reconciled),
    getAccountBalance(Dao.Status, Param.account, Param.year, Param.reconciled),
    getAccountBalances(Dao.Status, Param.year, Param.reconciled),

    //more command definitions here...
    ;

    private final Dao dao;
    private final ArrayList<Param> params = new ArrayList<Param>();
    private final Boolean sessionRequired;

    Name(boolean sessionRequired, Dao dao, Param... params) {
      for (Param p : params) this.params.add(p);
      this.dao = dao;
      this.sessionRequired = sessionRequired;
    }

    Name(Dao dao, Param... params) {
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
     * Get the list of params defined for this Name;
     *
     * @return the list of parameters
     */
    public List<Param> getParams() {
      return params;
    }

    /**
     * Whether this command requires a session be established.
     *
     * @return Boolean
     */
    public Boolean isSessionRequired() {
      return sessionRequired;
    }

  }

  private final Name name;
  // the actual parameters
  private final HashMap<Param, Serializable> params =
      new HashMap<Param, Serializable>();
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
  public HashMap<Param, Serializable> getParams() {
    return params;
  }

  /**
   * Returns the value of the parameter defined by p
   *
   * @param p the Param key
   *
   * @return Serializable value
   */
  public Serializable getParam(Param p) {
    return params.get(p);
  }

  /**
   * Sets a parameter's value.
   *
   * @param param the Param key
   * @param value the Serializable value
   *
   * @return Command the instance of command (for chained method calls)
   */
  public Command set(Param param, Serializable value) {
    // if this command contains the parameter
    if (this.name.getParams().contains(param))
      // and the provided parameter is an instance of the parameter's type
      if (param.getType().isInstance(value))
        this.params.put(param, value);
      else
        throw new IllegalArgumentException(
            param.name()
            + " requires type "
            + param.getType().getSimpleName()
            + " and is not type "
            + value.getClass().getSimpleName());
    else
      throw new IllegalArgumentException("invalid parameter " + param.name());
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
    credentials.setSignature(Encryption.sign(key, String.valueOf(hashCode())));
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
  public boolean verify(PublicKey key) throws NoSuchAlgorithmException,
      SignatureException, UnsupportedEncodingException,
      InvalidKeyException {
    return Encryption.verify(
        key,
        String.valueOf(hashCode()),
        credentials.getSignature()
    );
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

    // step through each parameter
    for (Param param : params.keySet()) {
      // the hash of the ordinal value
      result += 13 * param.ordinal() + 113;
      // Class.class's hash code means something else
      // we'll use a hash on the simple name instead
      result += param.getType().getSimpleName().hashCode();
    }
    return result;
  }
}