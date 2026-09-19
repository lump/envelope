package net.lump.lib;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.regex.Pattern;

/**
 * Money is a bigdecimal which automatically reads currency and formats correctly on toString().
 *
 * @author Troy Bowman
 */
public class Money implements Serializable, Comparable<Money> {

  private BigDecimal value = BigDecimal.ZERO;
  private static final NumberFormat format = java.text.NumberFormat.getCurrencyInstance();
  private static final NumberFormat genericFormat = java.text.NumberFormat.getInstance();
  private static final Pattern vanilla = Pattern.compile("^-?\\d+(\\.\\d+)?$");

  public static final Money ZERO = new Money(0);

  /**
   * Attempts to parse the string with the current locale's Currency NumberFormat parser.  If that fails, it falls back to the
   * BigDecimal parser.
   *
   * @param val the value
   */
  public Money(String val) {

    // there's no such thing as null money.  If it's null, set it to zero if it's not already.
    if (val == null) value = BigDecimal.ZERO;
    else {
      String s = val.trim();

      // Accounting notation, handled here rather than by the formatter.  In 2009
      // the en_US currency pattern carried a negative subpattern of (¤#,##0.00),
      // so "($1.02)" parsed and toString() emitted it.  CLDR has since dropped it
      // -- the pattern is now ¤#,##0.00 and negatives render as -$1.02 -- which
      // left parenthesised input failing both formatters and throwing
      // NumberFormatException out of the last-ditch BigDecimal parse below.
      // Anyone typing "($5.00)" into the amount field hit that.
      final boolean parenthesised =
          s.length() > 1 && s.startsWith("(") && s.endsWith(")");
      if (parenthesised) s = s.substring(1, s.length() - 1).trim();

      try { // try generic locale format first
        value = new BigDecimal(String.valueOf(genericFormat.parse(s).doubleValue()));
      } catch (ParseException e) {
        try { // try currency format second
          value = new BigDecimal(format.parse(s).toString());
        } catch (ParseException e2) {
          // try a generic bigdecimal parse
          value = new BigDecimal(s);
          // and give up
        }
      }

      if (parenthesised) value = value.negate();
    }
    // HALF_EVEN, not HALF_UP.  This class's Javadoc and its toString() both promise
    // half-even ("the most often-used rounding in financial calculations"), but the
    // constructor rounded half-up and therefore destroyed the half-fraction before
    // toString() ever saw it: "1.025" became 1.03 here and printed $1.03, where
    // half-even is $1.02.  TestMoney.testPrint has asserted the documented
    // behaviour since 2009 and failed on it.
    value = value.setScale(format.getMaximumFractionDigits(), RoundingMode.HALF_EVEN);
  }

  /**
   * Create a Money from a double.
   *
   * @param val double
   */
  public Money(double val) {
    // BigDecimal.valueOf, not new BigDecimal(double): the latter takes the exact
    // binary expansion, so new Money(19.99) held
    // 19.989999999999998436805981327779591083526611328125 and did not equal
    // new Money("19.99") -- while toString() printed $19.99 for both, which made
    // it invisible.  Scaled the same way the String constructor scales.
    value = BigDecimal.valueOf(val)
        .setScale(format.getMaximumFractionDigits(), RoundingMode.HALF_EVEN);
  }

  /**
   * Create a Money from another Money.
   *
   * @param val Money
   */
  public Money(Money val) {
    value = new BigDecimal(val.value.toString());
  }

  /**
   * Create a Money from a BigInteger.
   *
   * @param val BigInteger
   */
  public Money(BigInteger val) {
    value = new BigDecimal(val);
  }

  /**
   * Create a Money from a BigDecimal.
   *
   * @param val BigDecimal
   */
  public Money(BigDecimal val) {
    value = val;
  }

  /**
   * Create a Money from a char array.
   *
   * @param in char[]
   */
  public Money(char[] in) {
    value = new BigDecimal(in);
  }

  /**
   * Create a Money from an int.
   *
   * @param val int
   */
  public Money(int val) {
    value = new BigDecimal(val);
  }

  /**
   * Create a Money from a long.
   *
   * @param val long
   */
  public Money(long val) {
    value = new BigDecimal(val);
  }

  public Money negate() {
    return new Money(value.negate());
  }

  public Money add(Money that) {
    return new Money(value.add(that.value));
  }

  public Money subtract(Money that) {
    return new Money(value.subtract(that.value));
  }

  public Money multiply(Money that) {
    return new Money(value.multiply(that.value));
  }

  public Money abs() {
    return new Money(value.abs());
  }

  public Money divide(Money divisor) {
    if (divisor.equals(new Money(BigDecimal.ZERO))) return new Money(BigDecimal.ZERO);
    return new Money(value.divide(divisor.value));
  }

  public Money divide(Money divisor, int scale, int roundingMode) {
    return new Money(value.divide(divisor.value, scale, roundingMode));
  }

  public Money divide(Money divisor, int scale, RoundingMode roundingMode) {
    return new Money(value.divide(divisor.value, scale, roundingMode));
  }

  public Money divide(Money divisor, int roundingMode) {
    return new Money(value.divide(divisor.value, roundingMode));
  }

  /**
   * Formats the value of this object with the current locale's currency number format.  Rounding used is the most often-used
   * rounding in financial calculations, which is "Half-Even".  Half-Even rounds half fractions to the even number (e.g., 2.5 rounds
   * to 2, while 3.5 rounds to 4), and fractions greater or less than half to the closest integer.
   *
   * @return String
   */
  public String toString() {
    return format.format((value.setScale(format.getMaximumFractionDigits(),
        BigDecimal.ROUND_HALF_EVEN)).doubleValue());
  }

  public BigDecimal toBigDecimal() {
    return value;
  }

  public double doubleValue() {
    return value.doubleValue();
  }

  public float floatValue() {
    return value.floatValue();
  }

  public int intValue() {
    return value.intValue();
  }

  public int compareTo(Money that) {
    return value.compareTo(that.value);
  }

  /**
   * An equals method which, contrary to the behavior of BigDecimal, actually doesn't care about scale in its comparison.
   *
   * @param that the object to compare to
   *
   * @return whether it equals
   */
  @Override
  public boolean equals(Object that) {
    if (this == that) return true;
    if (!(that instanceof Money)) return false;
    Money money = (Money)that;
    return value.compareTo(money.value) == 0;
  }

  @Override
  public int hashCode() {
    // equals() compares with compareTo, which ignores scale, so hashCode has to
    // ignore it too.  unscaledValue() did not: 1.21 and 1.2100 are equal and
    // hashed to 121 and 12100, which breaks the equals/hashCode contract for any
    // hash-based collection -- and Allocation.hashCode folds a Money in.
    // MoneyType.hashCode already normalized this way.
    return value.stripTrailingZeros().hashCode();
  }
}
