package us.lump.envelope.client;

import junit.framework.TestCase;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;
import org.junit.Test;
import us.lump.envelope.TestSuite;
import us.lump.envelope.client.portal.HibernatePortal;
import us.lump.envelope.shared.entity.Account;
import us.lump.envelope.shared.entity.Budget;
import us.lump.envelope.shared.entity.Identifiable;

/** Test Hibernate Operations. */
public class TestHibernate extends TestCase {

  @SuppressWarnings("unchecked")
  @Test
  public void testDetachedCriteria() throws Exception {
    DetachedCriteria criteria = DetachedCriteria.forClass(Account.class);
    criteria.add(Restrictions.eq("budget.id", 0));
    criteria.add(Restrictions.eq("name", "Guest's Checking"));
    HibernatePortal hp = new HibernatePortal();
    Account blah = (Account)hp.detachedCriteriaQueryUnique(criteria);
    System.out.println("Hello world");
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testLoad() throws Exception {


    HibernatePortal hp = new HibernatePortal();
    Identifiable b = hp.get(Budget.class, 0);


    System.out.println("Hello world");
  }

  protected void setUp() throws Exception {
    super.setUp();
    TestSuite.authed();
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }
}
