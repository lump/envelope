package us.lump.lib;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.text.ParseException;

/**
 * Extends BigDecimal to be able to have currency number format string parsing and formatting.
 *
 * @author Troy Bowman
 * @version $Id: Money.java,v 1.1 2007/07/21 20:15:04 troy Exp $
 */
public class Money extends BigDecimal implements Serializable {
  /**
   * Attempts to parse the string with the current locale's Currency NumberFormat parser.  If that fails, it falls back
   * to the BigDecimal parser.
   *
   * @param val the value
   */
  public Money(String val) {
    super(parseString(val));
  }

  /**
   * Create a Money from a double.
   *
   * @param val double
   */
  public Money(double val) {
    super(val);
  }

  /**
   * Create a Money from a BigInteger.
   *
   * @param val BigInteger
   */
  public Money(BigInteger val) {
    super(val);
  }

  /**
   * Create a Money from a BigDecimal.
   *
   * @param val BigDecimal
   */
  public Money(BigDecimal val) {
    super(val.toString());
  }

  /**
   * Create a Money from a char array.
   *
   * @param in char[]
   */
  public Money(char[] in) {
    super(in);
  }

  /**
   * Create a Money from an int.
   *
   * @param val int
   */
  public Money(int val) {
    super(val);
  }

  /**
   * Create a Money from a long.
   *
   * @param val long
   */
  public Money(long val) {
    super(val);
  }

  /**
   * Formats the value of this object with the current locale's currency number format.  Rounding used is the most
   * often-used rounding in financial calculations, which is "Half-Even".  Half-Even rounds half fractions to the even
   * number (e.g., 2.5 rounds to 2, while 3.5 rounds to 4), and fractions greater or less than half to the closest
   * integer.
   *
   * @return String
   */
  public String toFormattedString() {
    NumberFormat f = java.text.NumberFormat.getCurrencyInstance();
    return f.format((this.setScale(f.getMaximumFractionDigits(), BigDecimal.ROUND_HALF_EVEN)).doubleValue());
  }

  private static String parseString(String val) {
    // there's no such thing as null money (if there were, I'd be rich).
    // If it's null, it's really zero.
    if (val == null) val = "0.0";

    try {
      // try to use the current locale's currency to parse the string
      return (java.text.NumberFormat.getCurrencyInstance().parse(val)).toString();
    }
    catch (ParseException pe) {
      // fail over to plain BigDecimal parsing
      return val;
    }
  }
}
