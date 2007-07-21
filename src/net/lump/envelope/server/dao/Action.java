package us.lump.envelope.server.dao;

import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

import java.util.ArrayList;
import java.util.List;

/**
 * A DAO for Transactions.
 *
 * @author Troy Bowman
 * @version $Id: Action.java,v 1.1 2007/07/21 20:15:04 troy Exp $
 */
public class Action extends DAO {

  public String ping() {
    return "pong";
  }

  /**
   * Lists all transactions for a given year.
   *
   * @param year of the transactions
   * @return the transaction list
   */
  public List<us.lump.envelope.entity.Transaction> listTransactions(Integer year) {
    List<Order> order = new ArrayList<Order>();
    order.add(Order.asc("date"));
    order.add(Order.asc("stamp"));
    List<us.lump.envelope.entity.Transaction> list = list(
        us.lump.envelope.entity.Transaction.class,
        order,
        Restrictions.conjunction()
            .add(Restrictions.sqlRestriction("year({alias}.date) = ?", year, org.hibernate.Hibernate.INTEGER)
        )
    );

    evict(list);

    logger.info("retrieved " + list.size() + " transactions in " + year);
    return list;
  }
}
