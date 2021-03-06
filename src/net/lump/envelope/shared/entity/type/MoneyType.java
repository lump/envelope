package net.lump.envelope.shared.entity.type;

import org.hibernate.EntityMode;
import org.hibernate.HibernateException;
import org.hibernate.type.ImmutableType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * A MoneyType type for Hibernate.
 *
 * @author Troy Bowman
 * @version $Id: MoneyType.java,v 1.2 2009/10/02 22:06:23 troy Exp $
 */
public class MoneyType extends ImmutableType implements Serializable {
  public Object fromStringValue(String xml) {
    return new net.lump.lib.Money(xml);
  }

  public Object get(ResultSet rs, String name)
      throws HibernateException, SQLException {
    return new net.lump.lib.Money(rs.getString(name));
  }

  public int getHashCode(Object x, EntityMode entityMode) {
    return ((net.lump.lib.Money)x).intValue();
  }

  public String getName() {
    return "money";
  }

  public Class getReturnedClass() {
    return net.lump.lib.Money.class;
  }

  public boolean isEqual(Object x, Object y) {
    return x == y
           || (x != null && y != null && ((net.lump.lib.Money)x)
        .compareTo((net.lump.lib.Money)y) == 0);
  }

  public void set(PreparedStatement st, Object value, int index)
      throws HibernateException, SQLException {
    st.setString(index, value.toString());
  }

  public int sqlType() {
    return Types.NUMERIC;
  }

  public String toString(Object value) throws HibernateException {
    return value.toString();
  }
}
