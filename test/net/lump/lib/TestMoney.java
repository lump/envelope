package net.lump.lib;

import junit.framework.TestCase;
import org.junit.Test;

/**
 * Tests MoneyType.
 *
 * @author Troy Bowman
 * @version $Id: TestMoney.java,v 1.5 2010/01/04 06:07:24 troy Exp $
 */
public class TestMoney extends TestCase {

  /**
   * Tests the rounding of the Money object.
   *
   * @throws Exception
   */
  @Test
  public void testPrint() throws Exception {

// ROUND_HALF_EVEN rounds up only when the number in the
// previous column to the number being rounded is odd.
//    0.005 rounded is $0.00
//    0.015 rounded is $0.02
//    0.025 rounded is $0.02
//    0.035 rounded is $0.04
//    0.045 rounded is $0.04
//    0.055 rounded is $0.06
//    0.065 rounded is $0.06
//    0.075 rounded is $0.08
//    0.085 rounded is $0.08
//    0.095 rounded is $0.10

// generate list above
//    for (int x = 5; x < 100; x+=10) {
//      String fraction = pad(x, 3);
//      BigDecimal bd = new BigDecimal("0." + fraction);
//      MoneyType money = new MoneyType(bd.toString());
//      System.out.println(bd + " rounded is " + money.toFormattedString());
//    }


    String[] in =
        {"$1.025", "($1.025)", "1.035", "-1.045", "1.255", "1.265", "1.275"};
    String[] out =
        {"$1.02", "($1.02)", "$1.04", "($1.04)", "$1.26", "$1.26", "$1.28"};

    for (int x = 0; x < in.length; x++) {
      Money money = new Money(in[x]);
      assertEquals(out[x], money.toString());
    }

// seeing what happens for everything
//    for (int x = 1; x < 1000; x++) {
//      MoneyType money = new MoneyType("0." + pad(x, 4));
//      System.out.println(x + " " + money.toString() + " " +money.toFormattedString());
//    }

  }

  String pad(int in, int pad) {
    String out = Integer.toString(in);
    for (int x = 0; x <= pad - out.length(); x++) {
      out = "0" + out;
    }
    return out;
  }

  protected void setUp() throws Exception {
    super.setUp();
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }

}
