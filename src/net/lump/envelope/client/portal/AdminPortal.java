package net.lump.envelope.client.portal;

import net.lump.envelope.shared.command.Command;
import net.lump.envelope.client.ui.prefs.LoginSettings;
import net.lump.envelope.shared.command.security.Crypt;
import net.lump.envelope.shared.command.security.Scram;
import net.lump.envelope.shared.command.security.ScramChallenge;
import net.lump.envelope.shared.exception.EnvelopeException;
import net.lump.envelope.shared.entity.Budget;
import net.lump.envelope.shared.entity.User;
import net.lump.envelope.shared.exception.AbortException;

import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Users and budgets.  Everything here except whoAmI and setting one's own
 * password needs ADMIN, and the server is what enforces that -- the client only
 * decides what to draw.
 *
 * <p>Passwords never leave the client in the clear.  A new one is hashed here,
 * with a fresh salt, exactly as the login exchange hashes one, and the hash is
 * what travels; the server stores it as given.
 *
 * @author troy
 */
@SuppressWarnings({"unchecked"})
public class AdminPortal extends Portal {

  /** The md5-crypt hash of a password under a new random salt. */

  /** The caller: name, budget and permissions.  Never the password. */
  public User whoAmI() throws AbortException {
    return (User)invoke(new Command(Command.Name.whoAmI, null));
  }

  public List<User> listUsers() throws AbortException {
    return (List<User>)invoke(new Command(Command.Name.listUsers, null));
  }

  public List<Budget> listBudgets() throws AbortException {
    return (List<Budget>)invoke(new Command(Command.Name.listBudgets, null));
  }

  /** Add a user; the password travels as the verifier computed from it here. */
  public User createUser(String name, String realName, Integer budgetId, Long permissions,
                         String password) throws AbortException, GeneralSecurityException {
    Scram.Verifier v = Scram.newVerifier(password);
    return (User)invoke(new Command(Command.Name.createUser, null,
        name, realName, budgetId, permissions, v.md5Salt, v.salt, v.iterations, v.storedKey, v.serverKey));
  }

  public void updateUser(Integer userId, String realName, Integer budgetId, Long permissions)
      throws AbortException {
    invoke(new Command(Command.Name.updateUser, null, userId, realName, budgetId, permissions));
  }

  /**
   * Set a user's password, as the verifier computed from it here.
   *
   * @param currentPassword one's own current password, to prove with when setting
   *                        one's own; null when an administrator sets someone else's
   */
  public void setPassword(Integer userId, String userName, String password, String currentPassword)
      throws AbortException, GeneralSecurityException, EnvelopeException {
    Scram.Verifier v = Scram.newVerifier(password);
    String nonce = null;
    byte[] proof = null;
    java.security.PublicKey mine = null;
    if (currentPassword != null) {
      // an ordinary SCRAM exchange for one's own name, its proof handed to
      // setPassword in place of scramFinish
      String clientNonce = Scram.newNonce();
      ScramChallenge c = (ScramChallenge)invoke(new Command(Command.Name.scramStart, null, userName, clientNonce));
      byte[] salted = Scram.saltedPassword(Crypt.crypt(c.md5Salt(), currentPassword), c.salt(), c.iterations());
      mine = LoginSettings.getInstance().getKeyPair().getPublic();
      nonce = c.nonce();
      proof = Scram.clientProof(Scram.clientKey(salted),
          Scram.authMessage(userName, clientNonce, nonce, c.salt(), c.iterations(), mine));
    }
    invoke(new Command(Command.Name.setPassword, null,
        userId, v.md5Salt, v.salt, v.iterations, v.storedKey, v.serverKey, nonce, proof, mine));
  }

  public Budget createBudget(String name) throws AbortException {
    return (Budget)invoke(new Command(Command.Name.createBudget, null, name));
  }

  public void deleteBudget(Integer budgetId) throws AbortException {
    invoke(new Command(Command.Name.deleteBudget, null, budgetId));
  }
}
