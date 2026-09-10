package net.lump.envelope.client;

import net.lump.envelope.client.ui.MainFrame;
import net.lump.envelope.client.ui.components.Hierarchy;
import net.lump.envelope.client.ui.prefs.LoginSettings;
import net.lump.envelope.shared.entity.Account;
import net.lump.envelope.shared.entity.AllocationPreset;
import net.lump.envelope.shared.entity.Budget;
import net.lump.envelope.shared.entity.Category;
import net.lump.envelope.shared.exception.AbortException;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

/**
 * This is a repository for globally accessed object instances.
 *
 * @version $Id: State.java,v 1.9 2009/10/02 22:06:23 troy Exp $
 */
public class State {

  private static State singleton;

  private MainFrame mainFrame;
  private Hierarchy hierarchy;
  private Budget budget;
  private TreeSet<Account> accounts = new TreeSet<Account>();
  private HashMap<Account, List<Category>> categories =
      new HashMap<Account, List<Category>>();
  /**
   * The budget's allocation preset rows, held so the allocation menu can be built
   * on the event thread without going to the server to do it.  Refreshed whenever
   * a transaction is loaded into the form.
   */
  private List<AllocationPreset> presets = new ArrayList<AllocationPreset>();

  private static final int cacheSize = 32;
  private static final int cacheTtl = 20;
  private static final int cacheTti = 10;
  private static final String ENTITIES = "entities";
  Cache entityCache;

  private State() {
    entityCache =
        new Cache(ENTITIES, cacheSize, false, false, cacheTtl, cacheTti);
    CacheManager.getInstance().addCache(entityCache);
  }

  public static State getInstance() {
    if (singleton == null) singleton = new State();
    return singleton;
  }

  public Budget getBudget() throws AbortException {
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

  public List<AllocationPreset> getPresets() {
    return presets;
  }

  public void setPresets(List<AllocationPreset> presets) {
    this.presets = presets == null ? new ArrayList<AllocationPreset>() : presets;
  }

  /** The distinct preset names, in the order they were read. */
  public List<String> getPresetNames() {
    List<String> names = new ArrayList<String>();
    for (AllocationPreset p : presets)
      if (!names.contains(p.getName())) names.add(p.getName());
    return names;
  }

  public HashMap<Account, List<Category>> getCategories() {
    return categories;
  }

  public void setCategories(HashMap<Account, List<Category>> categories) {
    this.categories = categories;
  }

  @SuppressWarnings({"unchecked"})
  public List<String> entities() throws AbortException {
    Element element = entityCache.get(ENTITIES);
    if (element != null)
      return (List<String>)element.getValue();
    else {
      java.util.List<String> entities;
      entities = CriteriaFactory.getInstance().getEntitiesforBudget(State.getInstance().getBudget());
      entityCache.put(new Element(ENTITIES, entities));
      return entities;
    }
  }
}
