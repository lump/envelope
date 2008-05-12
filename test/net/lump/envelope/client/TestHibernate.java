package us.lump.envelope.client;

import junit.framework.TestCase;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;
import org.junit.Test;
import us.lump.envelope.TestSuite;
import us.lump.envelope.client.portal.HibernatePortal;
import us.lump.envelope.entity.Account;

import java.util.List;

/** Test Hibernate Operations. */
public class TestHibernate extends TestCase {

  @SuppressWarnings("unchecked")
  @Test
  public void testLogin() throws Exception {
    TestSuite.authed();
    DetachedCriteria criteria = DetachedCriteria.forClass(Account.class);
    criteria.add(Restrictions.eq("budget.id", 0));
    criteria.add(Restrictions.eq("name", "Guest's Checking"));
    HibernatePortal hp = new HibernatePortal();
    List l = hp.detachedCriteriaQuery(criteria);


    System.out.println("Hello world");
  }

  protected void setUp() throws Exception {
    super.setUp();
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }
}
