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
 * @version $Id: Money.java,v 1.7 2010/01/04 06:07:24 troy Exp $
 */
public class Money implements Serializable, Comparable<Money> {

  private BigDecimal value = BigDecimal.ZERO;
  private static final NumberFormat format = java.text.NumberFormat.getCurrencyInstance();
  private static final Pattern vanilla = Pattern.compile("^-?\\d+(\\.\\d+)?$");

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
      if (vanilla.matcher(val).matches())
        value = new BigDecimal(val);
      else
        try {
          // try to use the current locale's currency to parse the string
          Number n = format.parse(val);
          value = n == null ? BigDecimal.ZERO : new BigDecimal(n.toString());
        }
        catch (ParseException pe) {
          // fail over to plain BigDecimal parsing
          value = new BigDecimal(val);
        }
    }
    value = value.setScale(format.getMaximumFractionDigits());
  }

  /**
   * Create a Money from a double.
   *
   * @param val double
   */
  public Money(double val) {
    value = new BigDecimal(val);
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
    return value.unscaledValue().hashCode();
  }
}
