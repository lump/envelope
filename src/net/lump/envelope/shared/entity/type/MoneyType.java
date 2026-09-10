package net.lump.envelope.shared.entity.type;

import net.lump.lib.Money;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * A MoneyType type for Hibernate.
 *
 * <p>This used to extend org.hibernate.type.ImmutableType, an internal Hibernate 3.x
 * core-type class that no longer exists.  It is now a plain UserType, which is the
 * supported extension point.
 *
 * <p>It also used to bind through {@link Money#toString()}, which returns a
 * <em>locale currency-formatted</em> string ("$5.30") -- the database was being handed
 * "$5.30" for a NUMERIC column and silently coercing it.  Binding goes through
 * BigDecimal now, which is what the column actually holds.
 *
 * @author Troy Bowman
 * @version $Id: MoneyType.java,v 1.2 2009/10/02 22:06:23 troy Exp $
 */
public class MoneyType implements UserType, Serializable {

  private static final int[] SQL_TYPES = {Types.NUMERIC};

  public int[] sqlTypes() {
    return SQL_TYPES.clone();
  }

  public Class<Money> returnedClass() {
    return Money.class;
  }

  public boolean equals(Object x, Object y) throws HibernateException {
    return x == y
           || (x != null && y != null && ((Money)x).compareTo((Money)y) == 0);
  }

  public int hashCode(Object x) throws HibernateException {
    return x == null ? 0 : ((Money)x).toBigDecimal().stripTrailingZeros().hashCode();
  }

  public Object nullSafeGet(ResultSet rs,
                            String[] names,
                            SharedSessionContractImplementor session,
                            Object owner)
      throws HibernateException, SQLException {
    BigDecimal value = rs.getBigDecimal(names[0]);
    return rs.wasNull() ? null : new Money(value);
  }

  public void nullSafeSet(PreparedStatement st,
                          Object value,
                          int index,
                          SharedSessionContractImplementor session)
      throws HibernateException, SQLException {
    if (value == null) st.setNull(index, Types.NUMERIC);
    else st.setBigDecimal(index, ((Money)value).toBigDecimal());
  }

  /** Money wraps an immutable BigDecimal, so it is its own deep copy. */
  public Object deepCopy(Object value) throws HibernateException {
    return value;
  }

  public boolean isMutable() {
    return false;
  }

  public Serializable disassemble(Object value) throws HibernateException {
    return (Serializable)value;
  }

  public Object assemble(Serializable cached, Object owner)
      throws HibernateException {
    return cached;
  }

  public Object replace(Object original, Object target, Object owner)
      throws HibernateException {
    return original;
  }
}
