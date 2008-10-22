package us.lump.envelope.client;

import us.lump.envelope.client.ui.MainFrame;
import us.lump.envelope.client.ui.components.Hierarchy;
import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.entity.Account;
import us.lump.envelope.entity.Budget;
import us.lump.envelope.entity.Category;

import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

/**
 * This is a repository for globally accessed object instances.
 * 
 * @version $Id: State.java,v 1.4 2008/10/22 04:01:30 troy Exp $
 */
public class State {

  private static State singleton;
  private State() {}

  private MainFrame mainFrame;
  private Hierarchy hierarchy;
  private Budget budget;
  private TreeSet<Account> accounts = new TreeSet<Account>();
  private HashMap<Account, List<Category>> categories =
      new HashMap<Account, List<Category>>();

  public static State getInstance() {
    if (singleton == null) singleton = new State();
    return singleton;
  }

  public Budget getBudget() {
    if (budget == null)
      budget = CriteriaFactory.getInstance().getBudgetForUser(
          LoginSettings.getInstance().getUsername());
    return budget;
  }

  public void setBudget(Budget budget) {
    this.budget = budget;
  }

  public TreeSet<Account> getAccounts() {
    return accounts;
  }

  public void setAccounts(TreeSet<Account> accounts) {
    this.accounts = accounts;
  }

  public HashMap<Account, List<Category>> getCategories() {
    return categories;
  }

  public void setCategories(HashMap<Account, List<Category>> categories) {
    this.categories = categories;
  }
}
