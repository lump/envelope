package us.lump.envelope.client;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import us.lump.envelope.client.ui.MainFrame;
import us.lump.envelope.client.ui.components.Hierarchy;
import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.entity.Account;
import us.lump.envelope.entity.Budget;
import us.lump.envelope.entity.Category;
import us.lump.envelope.exception.EnvelopeException;

import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

/**
 * This is a repository for globally accessed object instances.
 * 
 * @version $Id: State.java,v 1.5 2008/11/01 00:53:02 troy Exp $
 */
public class State {

  private static State singleton;

  private MainFrame mainFrame;
  private Hierarchy hierarchy;
  private Budget budget;
  private TreeSet<Account> accounts = new TreeSet<Account>();
  private HashMap<Account, List<Category>> categories =
      new HashMap<Account, List<Category>>();

  private static final int cacheSize = 32;
  private static final int cacheTtl = 20;
  private static final int cacheTti = 10;
  private static final String ENTITIES = "entities";
  Cache entityCache;

  private State() {
    entityCache = new Cache(ENTITIES, cacheSize, false, false, cacheTtl, cacheTti);
    CacheManager.getInstance().addCache(entityCache);
  }

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

  public List<String> entities() {
    Element element = entityCache.get(ENTITIES);
    if (element != null) //noinspection unchecked
      return (List<String>)element.getValue();
    else {
      java.util.List<String> entities;
      try {
        entities = CriteriaFactory.getInstance()
            .getEntitiesforBudget(State.getInstance().getBudget());
        entityCache.put(new Element(ENTITIES, entities));
        return entities;
      } catch (EnvelopeException e) {
        e.printStackTrace();
        return null;
      }
    }
  }
}
