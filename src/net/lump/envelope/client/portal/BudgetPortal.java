package net.lump.envelope.client.portal;

import net.lump.envelope.shared.command.Command;
import net.lump.envelope.shared.entity.Account;
import net.lump.envelope.shared.entity.Category;
import net.lump.envelope.shared.exception.AbortException;

/**
 * The shape of a budget: its accounts, and the categories under them.
 *
 * <p>Separate from TransactionPortal because these change the structure money is
 * filed into rather than the money itself.  Each is a narrow command on the Action
 * facet rather than a generic save or delete -- nothing on the server checks whose
 * budget a command touches, so a generic verb would let any authenticated caller
 * alter any row of that type in anyone's budget.
 *
 * @author troy
 */
@SuppressWarnings({"unchecked"})
public class BudgetPortal extends Portal {

  /**
   * Add an account to a budget.
   *
   * @param budgetId the budget it belongs to
   * @param name     the account name, unique within that budget
   * @param type     one of Account.AccountType -- Debit, Credit or Loan
   *
   * @return the new Account
   */
  public Account createAccount(Integer budgetId, String name, Account.AccountType type)
      throws AbortException {
    return (Account)invoke(
        new Command(Command.Name.createAccount, null, budgetId, name, type.name()));
  }

  /** Rename an account.  The name must stay unique within its budget. */
  public void renameAccount(Integer accountId, String name) throws AbortException {
    invoke(new Command(Command.Name.renameAccount, null, accountId, name));
  }

  /** Remove an account.  The server refuses while it still holds categories. */
  public void deleteAccount(Integer accountId) throws AbortException {
    invoke(new Command(Command.Name.deleteAccount, null, accountId));
  }

  /**
   * Add a category to an account.
   *
   * @param accountId the account it belongs to
   * @param name      the category name, unique within that account
   *
   * @return the new Category
   */
  public Category createCategory(Integer accountId, String name) throws AbortException {
    return (Category)invoke(new Command(Command.Name.createCategory, null, accountId, name));
  }

  /** Rename a category.  The name must stay unique within its account. */
  public void renameCategory(Integer categoryId, String name) throws AbortException {
    invoke(new Command(Command.Name.renameCategory, null, categoryId, name));
  }

  /** Remove a category.  The server refuses while allocations still point at it. */
  public void deleteCategory(Integer categoryId) throws AbortException {
    invoke(new Command(Command.Name.deleteCategory, null, categoryId));
  }

  /**
   * Remove one row from an allocation preset.  Adding and changing rows needs no
   * command of its own: AllocationPreset is an Identifiable, so HibernatePortal's
   * saveOrUpdate carries it.
   */
  public void deletePresetRow(Integer presetRowId) throws AbortException {
    invoke(new Command(Command.Name.deletePresetRow, null, presetRowId));
  }

  /** Remove a whole named preset from a budget, every row of it. */
  public void deletePresetNamed(Integer budgetId, String name) throws AbortException {
    invoke(new Command(Command.Name.deletePresetNamed, null, budgetId, name));
  }

  /** Rename a preset, which means renaming every row that carries the name. */
  public void renamePresetNamed(Integer budgetId, String oldName, String newName)
      throws AbortException {
    invoke(new Command(Command.Name.renamePresetNamed, null, budgetId, oldName, newName));
  }
}
