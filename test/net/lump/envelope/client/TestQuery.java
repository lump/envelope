package us.lump.envelope.client;

import junit.framework.TestCase;
import org.junit.Test;
import us.lump.envelope.TestSuite;
import us.lump.envelope.client.portal.SecurityPortal;
import us.lump.envelope.client.portal.TransactionPortal;
import us.lump.envelope.entity.Account;
import us.lump.envelope.entity.Category;
import us.lump.envelope.entity.Transaction;
import us.lump.lib.Money;

import java.sql.Date;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * Test queries.
 *
 * @author troy
 * @version $Id: TestQuery.java,v 1.1 2008/01/20 05:15:41 troy Exp $
 */
public class TestQuery extends TestCase {

  // pings
  @Test public void testPing() throws Exception {
    assertTrue("Couldn't do authed ping", new SecurityPortal().rawPing());
  }

  @Test public void testAuthedPing() throws Exception {
    assertTrue("Couldn't do authed ping", new SecurityPortal().authedPing());
  }

  // transaction
  @Test public void testListTransactionsBetweenDates() throws Exception {
    List<Transaction> list = new TransactionPortal().listTransactions(
        new Date(System.currentTimeMillis()),
        new Date(System.currentTimeMillis() - (86400000L * 30L)));
    assertTrue("list size is not > 0", list.size() > 0);
  }

  @SuppressWarnings("unchecked")
  @Test public void testListTransactionsInYear() throws Exception {

    List<Transaction> list = new TransactionPortal().listTransactions(
        new GregorianCalendar().get(Calendar.YEAR));
    assertTrue("list size is not > 0", list.size() > 0);
  }

  @Test public void testCategoryGetBalance() {
    TransactionPortal tp = new TransactionPortal();
    Category c = tp.getCategory("Groceries");
    Money allBalance = tp.getCategoryBalance(c, null);
    Money recBalance = tp.getCategoryBalance(c, Boolean.TRUE);
    Money unRecBalance = tp.getCategoryBalance(c, Boolean.FALSE);

    assertTrue("Total balance is not > 0", allBalance.doubleValue() > 0.0D);
    assertTrue("Reconciled balance is larger than total balance",
               recBalance.doubleValue() <= allBalance.doubleValue());
    assertTrue("Unreconciled balance is larger than total balance",
               unRecBalance == null ||
               unRecBalance.doubleValue() <= allBalance.doubleValue());
  }

  @Test public void testCategoryGetBalances() {
    TransactionPortal tp = new TransactionPortal();
    List<Object> allList = tp.getCategoryBalances(null);
    List<Object> recList = tp.getCategoryBalances(true);
    List<Object> unRecList = tp.getCategoryBalances(false);
    assertTrue("Nothing returned", allList.size() > 0);
    assertTrue("Nothing returned", recList.size() > 0);
    assertTrue("Nothing returned", unRecList.size() > 0);
    assertTrue("Reconciled list is larger than total list",
               recList.size() <= allList.size());
    assertTrue("Unreconciled list is larger than total list",
               unRecList.size() <= allList.size());
  }

  @Test public void testAccountGetBalance() {
    TransactionPortal tp = new TransactionPortal();
    Account a = tp.getAccount("Checking");
    Money allBalance = tp.getAccountBalance(a, null);
    Money recBalance = tp.getAccountBalance(a, Boolean.TRUE);

    assertTrue("Total balance is not > 0", allBalance.doubleValue() > 0.0D);
    assertTrue("Reconciled balance is not > 0",
               recBalance.doubleValue() > 0.0D);
  }

  @Test public void testAccountGetBalances() {
    TransactionPortal tp = new TransactionPortal();
    List<Object> allList = tp.getAccountBalances(null);
    List<Object> recList = tp.getAccountBalances(true);
    List<Object> unRecList = tp.getAccountBalances(false);
    assertTrue("Nothing returned", allList.size() > 0);
    assertTrue("Nothing returned", recList.size() > 0);
    assertTrue("Nothing returned", unRecList.size() > 0);
    assertTrue("Reconciled list is larger than total list",
               recList.size() <= allList.size());
    assertTrue("Unreconciled list is larger than total list",
               unRecList.size() <= allList.size());
  }


  protected void setUp() throws Exception {
    super.setUp();
    TestSuite.authed();
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }
}
