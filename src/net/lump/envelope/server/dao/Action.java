package us.lump.envelope.server.dao;

import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Projections;
import us.lump.envelope.entity.Transaction;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * A DAO for Transactions.
 *
 * @author Troy Bowman
 * @version $Id: Action.java,v 1.7 2008/07/17 03:30:40 troy Exp $
 */
public class Action extends DAO {

  /**
   * Sets reconciled
   * @param transactionID the ID of the transaction
   * @param reconciled whether this is reconciled or not
   * @return Transaction
   */
  public void updateReconciled(Integer transactionID, Boolean reconciled) {
    Transaction t = load(Transaction.class, transactionID);
    t.setReconciled(reconciled);
  }

  /**
   * Lists all transactions for a given year.
   *
   * @param year of the transactions
   *
   * @return the transaction list
   */
  public List<Transaction> listTransactionsInYear(Integer year) {
    List<Order> order = new ArrayList<Order>();
    order.add(Order.asc("date"));
    order.add(Order.asc("stamp"));
    List<us.lump.envelope.entity.Transaction> list = list(
        us.lump.envelope.entity.Transaction.class,
        order,
        Restrictions.conjunction()
            .add(Restrictions.sqlRestriction("year({alias}.date) = ?",
                                             year,
                                             org.hibernate.Hibernate.INTEGER)
        )
    );

    evict(list);

    logger.info("retrieved " + list.size() + " transactions in " + year);
    return list;
  }

  /**
   * Lists transactions between dates.
   *
   * @param date1 one date limit
   * @param date2 other data limit
   *
   * @return List<Transaction>
   */
  @SuppressWarnings({"unchecked"})
  public List<Transaction> listTransactionsBetweenDates(Date date1,
                                                        Date date2) {

    Date startDate = date1.before(date2) ? date1 : date2;
    Date endDate = date2.after(date1) ? date2 : date1;

    Criteria criteria = getCurrentSession().createCriteria(Transaction.class);
    criteria.add(Restrictions.conjunction()
        .add(Restrictions.between("date", startDate, endDate)))
        .createCriteria("allocations")
        .createCriteria("category")
        .createCriteria("account")
        .add(Restrictions.eq("budget", ThreadInfo.getUser().getBudget()))
        .setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);

    List<Transaction> list = criteria.list();
    evict(list);
    logger.info("retrieved " + list.size() + " transactions for dates between "
                + startDate.toString() + " and " + endDate.toString());

    return list;
  }

}
