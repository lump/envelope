package net.lump.envelope.client.portal;

import net.lump.envelope.shared.command.Command;
import net.lump.envelope.shared.command.security.Crypt;
import net.lump.envelope.shared.entity.Budget;
import net.lump.envelope.shared.entity.User;
import net.lump.envelope.shared.exception.AbortException;

import java.security.NoSuchAlgorithmException;
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
  public static String hash(String password) throws NoSuchAlgorithmException {
    return Crypt.crypt(Crypt.generateNewMd5Salt(), password);
  }

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

  public User createUser(String name, String realName, Integer budgetId, Long permissions,
                         String password) throws AbortException, NoSuchAlgorithmException {
    return (User)invoke(new Command(Command.Name.createUser, null,
        name, realName, budgetId, permissions, hash(password)));
  }

  public void updateUser(Integer userId, String realName, Integer budgetId, Long permissions)
      throws AbortException {
    invoke(new Command(Command.Name.updateUser, null, userId, realName, budgetId, permissions));
  }

  public void setPassword(Integer userId, String password)
      throws AbortException, NoSuchAlgorithmException {
    invoke(new Command(Command.Name.setPassword, null, userId, hash(password)));
  }

  public Budget createBudget(String name) throws AbortException {
    return (Budget)invoke(new Command(Command.Name.createBudget, null, name));
  }

  public void deleteBudget(Integer budgetId) throws AbortException {
    invoke(new Command(Command.Name.deleteBudget, null, budgetId));
  }
}
