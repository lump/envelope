package us.lump.envelope.client.portal;

import us.lump.envelope.Command;
import us.lump.envelope.entity.Account;
import us.lump.envelope.entity.Category;
import us.lump.envelope.entity.Transaction;
import us.lump.lib.Money;

import java.sql.Date;
import java.util.List;

/**
 * Transaction Methods.
 *
 * @author troy
 * @version $Id: TransactionPortal.java,v 1.3 2008/01/20 05:15:41 troy Exp $
 */
@SuppressWarnings({"unchecked"})
public class TransactionPortal extends Portal {


  public List<Transaction> listTransactions(int year) {
    return (List<Transaction>)
        invoke(new Command(Command.Name.listTransactionsInYear, year));
  }

  public List<Transaction> listTransactions(Date date1, Date date2) {
    return (List<Transaction>)invoke(
        new Command(Command.Name.listTransactionsBetweenDates, date1, date2));
  }

  public Category getCategory(String name) {
    return (Category)invoke(new Command(Command.Name.getCategory, name));
  }

  public Account getAccount(String name) {
    return (Account)invoke(new Command(Command.Name.getAccount, name));
  }

  public Money getCategoryBalance(Category category, Boolean reconciled) {
    return (Money)invoke(
        new Command(Command.Name.getCategoryBalance, category, reconciled));
  }

  public List<Object> getCategoryBalances(Boolean reconciled) {
    return (List<Object>)invoke(
        new Command(Command.Name.getCategoryBalances, reconciled));
  }

  public Money getAccountBalance(Account account, Boolean reconciled) {
    return (Money)invoke(
        new Command(Command.Name.getAccountBalance, account, reconciled));
  }

  public List<Object> getAccountBalances(Boolean reconciled) {
    return (List<Object>)invoke(
        new Command(Command.Name.getAccountBalances, reconciled));
  }
}
