package us.lump.envelope.server.dao;

import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import us.lump.envelope.entity.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * A DAO for Transactions.
 *
 * @author Troy Bowman
 * @version $Id: Action.java,v 1.3 2007/08/18 23:20:11 troy Exp $
 */
public class Action extends DAO {

  public Boolean authedPing() { return ping(); }

  public Boolean ping() { return true; }

  /**
   * Lists all transactions for a given year.
   *
   * @param year of the transactions
   *
   * @return the transaction list
   */
  public List<Transaction> listTransactions(Integer year) {
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
}
