package net.lump.envelope.shared.entity.type;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import net.lump.lib.Money;

import java.math.BigDecimal;

/**
 * Maps a {@link Money} to the DECIMAL column it is stored in.
 *
 * <p>This was {@code MoneyType}, a Hibernate {@code UserType}, whose interface
 * changed shape in every major version -- it was rewritten once for the move off
 * {@code ImmutableType} and would have been again for 6 and 7.  A Jakarta
 * Persistence converter is part of the standard, not Hibernate's SPI, so it does
 * not move when Hibernate does.  Money is immutable, which is all a converter
 * needs: there is nothing to deep-copy.
 *
 * <p>An aggregate over a converted column -- {@code sum(a.amount)} -- is not
 * guaranteed to come back as Money; {@code Query.money} takes either.
 */
@Converter
public class MoneyConverter implements AttributeConverter<Money, BigDecimal> {

  @Override
  public BigDecimal convertToDatabaseColumn(Money money) {
    return money == null ? null : money.toBigDecimal();
  }

  @Override
  public Money convertToEntityAttribute(BigDecimal value) {
    return value == null ? null : new Money(value);
  }
}
