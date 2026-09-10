-- 004: take allocation_presets.allocation off a float.
--
-- The column held `double`, and it carries two different kinds of number: a fixed
-- amount of money, and a percentage rate.  Money is only ever pennies and has no
-- business in a float -- everything else in this schema uses decimal, and Money
-- itself is BigDecimal-backed and bound as Types.NUMERIC.
--
-- The rate genuinely wants the precision, though: it is deliberately carried to
-- many places so that the computed amount is exact before being rounded to the
-- nearest cent (the imported 401k rate is 7.60764626375748%).  One decimal column
-- wide enough for both keeps the single-column design and stores each exactly:
-- 10 integer digits for the money, 14 fractional for the rate.
--
-- Applied to a live database with:
--   mariadb -h localhost -u budget -p envelope \
--     < sql/migrations/004-allocation-preset-decimal.sql

alter table allocation_presets
  modify `allocation` decimal(24,14) not null default 0;
