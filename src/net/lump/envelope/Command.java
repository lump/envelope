package us.lump.envelope;

import us.lump.envelope.entity.Account;
import us.lump.envelope.entity.Category;
import us.lump.envelope.server.security.Credentials;
import us.lump.lib.util.Encryption;

import java.io.Serializable;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * A command.
 *
 * @author Troy Bowman
 * @version $Revision: 1.2 $
 */
public class Command implements Serializable {
  /**
   * An application facet.
   *
   * @author Troy Bowman
   * @version $Revision: 1.2 $
   */
  public enum Dao {
    Security,
    Action,
    Report
  }

  /**
   * A parameter.
   *
   * @author Troy Bowman
   * @version $Revision: 1.2 $
   */
  public enum Param {
    public_key(String.class),
    user_name(String.class),
    challenge_response(String.class),

    year(Integer.class),

    account(Account.class),
    category(Category.class),
    reconciled(Boolean.class);


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
   * @version $Revision: 1.2 $
   */
  public enum Name {

    // diag
    ping(false, Dao.Action),
    authedPing(Dao.Action),

    // security
    getChallenge(false, Dao.Security, Param.user_name, Param.public_key),
    authChallengeResponse(false, Dao.Security, Param.user_name, Param.challenge_response),

    // transaction
    listTransactions(Dao.Action, Param.year),

    // report
    getCategoryBalance(Dao.Report, Param.category, Param.year, Param.reconciled),
    getCategoryBalances(Dao.Report, Param.year, Param.reconciled),
    getAccountBalance(Dao.Report, Param.account, Param.year, Param.reconciled),
    getAccountBalances(Dao.Report, Param.year, Param.reconciled),

    // end list
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
     * This is the facet of the command.  It refers directly to the class of DAO that will be called.
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
  private final HashMap<Param, Serializable> params = new HashMap<Param, Serializable>();
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
  public Name getCmd() {
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
   * @return Command the instance of command (for chained method calls)
   * @throws NoSuchAlgorithmException NoSuchAlgorithmException
   * @throws SignatureException       SignatureException
   * @throws InvalidKeyException      InvalidKeyException
   */
  public Command sign(String username, PrivateKey key)
          throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
    this.credentials = new Credentials(username);
    credentials.setSignature(Encryption.sign(key, String.valueOf(hashCode())));
    return this;
  }

  /**
   * Enum's hashcode does not compute the hashcode on the ordinal, and it is marked as final, so it can't be overridden.
   * Ugh, that's just yucky.
   * <p/>
   * So, here's a hashcode that uses the ordinal, a well as doing a hash on all of the attributes of this Command enum,
   * too.
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