package net.lump.envelope.client.portal;

import net.lump.envelope.client.ui.components.Hierarchy;
import net.lump.envelope.shared.command.Command;
import net.lump.envelope.shared.command.OutputListener;
import net.lump.envelope.shared.entity.Account;
import net.lump.envelope.shared.entity.AllocationPreset;
import net.lump.envelope.shared.entity.Budget;
import net.lump.envelope.shared.entity.Category;
import net.lump.envelope.shared.entity.Transaction;
import net.lump.envelope.shared.exception.AbortException;
import net.lump.envelope.shared.query.Total;
import net.lump.lib.Money;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every read the client makes, as a named question about the user's own budget.
 *
 * <p>This replaced CriteriaFactory, which built Hibernate {@code DetachedCriteria}
 * here and shipped them to a server that ran whatever arrived.  The queries
 * themselves now live in the server's {@code Query} DAO; this side sends only ids,
 * dates and booleans, and never says which budget -- the server takes that from
 * the logged-in user.  The methods keep the shapes the Swing code already used.
 *
 * @author troy
 */
@SuppressWarnings({"unchecked"})
public class QueryPortal extends Portal {

  /** The logged-in user's budget. */
  public Budget myBudget() throws AbortException {
    return (Budget)invoke(new Command(Command.Name.myBudget, null));
  }

  /** The distinct payees in the budget, for the entity field. */
  public List<String> entities() throws AbortException {
    return orEmpty((List<String>)invoke(new Command(Command.Name.entities, null)));
  }

  /** Every category in the budget, by name. */
  public List<Category> categories() throws AbortException {
    return orEmpty((List<Category>)invoke(new Command(Command.Name.categories, null)));
  }

  /** Every allocation preset row in the budget, in name order. */
  public List<AllocationPreset> presets() throws AbortException {
    return orEmpty((List<AllocationPreset>)invoke(new Command(Command.Name.presets, null)));
  }

  /** The budget's accounts with their balances, as the tree draws them. */
  public List<Hierarchy.AccountTotal> accountTotals() throws AbortException {
    List<Hierarchy.AccountTotal> out = new ArrayList<Hierarchy.AccountTotal>();
    for (Total<Account> t : orEmpty((List<Total<Account>>)invoke(new Command(Command.Name.accountTotals, null))))
      out.add(new Hierarchy.AccountTotal(t.item(), t.item().getName(), t.item().getId(), t.balance()));
    return out;
  }

  /** One account's categories with their balances, as the tree draws them. */
  public List<Hierarchy.CategoryTotal> categoryTotals(Account account) throws AbortException {
    List<Hierarchy.CategoryTotal> out = new ArrayList<Hierarchy.CategoryTotal>();
    for (Total<Category> t : orEmpty((List<Total<Category>>)invoke(
        new Command(Command.Name.categoryTotals, null, account.getId()))))
      out.add(new Hierarchy.CategoryTotal(t.item().getName(), t.item().getId(), t.balance()));
    return out;
  }

  /** One transaction with its allocations. */
  public Transaction transaction(Integer id) throws AbortException {
    return (Transaction)invoke(new Command(Command.Name.transaction, null, id));
  }

  /**
   * Every category's balance as of a day, without one transaction, keyed by
   * category id; a category with nothing to sum is absent.
   *
   * @param transactionId the transaction to leave out, or null for none
   * @param asOf          the last day to count, in the wire form, or null for all
   * @return the balances, or null if the read failed
   */
  public Map<Integer, Money> categoryBalances(Integer transactionId, Date asOf) throws AbortException {
    return (HashMap<Integer, Money>)invoke(
        new Command(Command.Name.categoryBalances, null, transactionId, asOf));
  }

  /** One category's balance as of a day, without one transaction. */
  public Money categoryBalance(Category category, Integer transactionId, Date asOf) throws AbortException {
    Money m = (Money)invoke(
        new Command(Command.Name.categoryBalance, null, category.getId(), transactionId, asOf));
    return m == null ? Money.ZERO : m;
  }

  /**
   * The balance of a tree node before a day.
   *
   * @param thing      a Hierarchy.AccountTotal or Hierarchy.CategoryTotal
   * @param reconciled true or false to count only those transactions, null for all
   */
  public Money beginningBalance(Object thing, Date before, Boolean reconciled) throws AbortException {
    Command c = thing instanceof Hierarchy.AccountTotal
        ? new Command(Command.Name.accountBeginningBalance, null, accountOf(thing), before, reconciled)
        : new Command(Command.Name.categoryBeginningBalance, null, categoryOf(thing), before, reconciled);
    Money m = (Money)invoke(c);
    return m == null ? Money.ZERO : m;
  }

  /**
   * A tree node's rows between two days, streamed to the listener as they arrive.
   * An account gives one row per transaction, a category one per allocation; see
   * the server's Query for the columns.
   */
  public List<Object[]> transactions(Object thing, Date begin, Date end, OutputListener listener)
      throws AbortException {
    Command c = thing instanceof Hierarchy.AccountTotal
        ? new Command(Command.Name.accountTransactions, listener, accountOf(thing), begin, end)
        : new Command(Command.Name.categoryTransactions, listener, categoryOf(thing), begin, end);
    return orEmpty((List<Object[]>)invoke(c));
  }

  private static Integer accountOf(Object thing) {
    return ((Hierarchy.AccountTotal)thing).account.getId();
  }

  private static Integer categoryOf(Object thing) {
    if (!(thing instanceof Hierarchy.CategoryTotal))
      throw new IllegalArgumentException("expected an AccountTotal or CategoryTotal, not " + thing);
    return ((Hierarchy.CategoryTotal)thing).id;
  }

  private static <T> List<T> orEmpty(List<T> list) {
    return list == null ? new ArrayList<T>() : list;
  }
}
