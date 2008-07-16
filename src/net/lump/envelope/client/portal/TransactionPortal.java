package us.lump.envelope.client.portal;

import us.lump.envelope.Command;
import us.lump.envelope.exception.EnvelopeException;
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
 * @version $Id: TransactionPortal.java,v 1.7 2008/07/16 05:40:00 troy Exp $
 */
@SuppressWarnings({"unchecked"})
public class TransactionPortal extends Portal {

  public void updateReconciled(Integer transactionId, Boolean reconciled)
      throws EnvelopeException {
    invoke(new Command(
        Command.Name.updateReconciled, transactionId, reconciled));
  }

  public List<Transaction> listTransactions(int year) throws EnvelopeException {
    return (List<Transaction>)
        invoke(new Command(Command.Name.listTransactionsInYear, year));
  }

  public List<Transaction> listTransactions(Date date1, Date date2) throws EnvelopeException {
    return (List<Transaction>)invoke(
        new Command(Command.Name.listTransactionsBetweenDates, date1, date2));
  }

  public Category getCategory(String name) throws EnvelopeException {
    return (Category)invoke(new Command(Command.Name.getCategory, name));
  }

  public Account getAccount(String name) throws EnvelopeException {
    return (Account)invoke(new Command(Command.Name.getAccount, name));
  }

  public Money getCategoryBalance(Category category, Boolean reconciled) throws EnvelopeException {
    return (Money)invoke(
        new Command(Command.Name.getCategoryBalance, category, reconciled));
  }

  public List<Object> getCategoryBalances(Boolean reconciled) throws EnvelopeException {
    return (List<Object>)invoke(
        new Command(Command.Name.getCategoryBalances, reconciled));
  }

  public Money getAccountBalance(Account account, Boolean reconciled) throws EnvelopeException {
    return (Money)invoke(
        new Command(Command.Name.getAccountBalance, account, reconciled));
  }

  public List<Object> getAccountBalances(Boolean reconciled) throws EnvelopeException {
    return (List<Object>)invoke(
        new Command(Command.Name.getAccountBalances, reconciled));
  }
}
