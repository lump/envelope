package us.lump.envelope.client;

import us.lump.envelope.entity.Budget;
import us.lump.envelope.entity.Account;
import us.lump.envelope.entity.Category;
import us.lump.envelope.client.ui.MainFrame;
import us.lump.envelope.client.ui.Hierarchy;
import us.lump.envelope.client.ui.prefs.LoginSettings;

import javax.swing.*;
import java.util.List;
import java.util.HashMap;
import java.util.TreeSet;

/**
 * This is a repository for globally accessed object instances.
 * 
 * @version $Id: State.java,v 1.2 2008/07/06 07:22:06 troy Exp $
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

  public MainFrame getMainFrame() {
    return mainFrame;
  }

  public void setMainFrame(MainFrame mainFrame) {
    this.mainFrame = mainFrame;
  }

  public Hierarchy getHierarchy() {
    return hierarchy;
  }

  public JTree setHierarchy(Hierarchy hierarchy) {
    this.hierarchy = hierarchy;
    return this.hierarchy;
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
