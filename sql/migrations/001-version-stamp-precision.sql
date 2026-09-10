-- 001: give the @Version stamp columns millisecond precision.
--
-- Every entity that Hibernate versions maps @Version onto a `stamp` column that
-- was declared plain `timestamp` -- whole seconds.  Hibernate generates the new
-- version value in milliseconds, so the value it hands back to the client can
-- never match the value the row actually holds.  The first save of an entity
-- succeeds; the second save of the same client-side copy compares .492 against
-- a stored .000, matches no row, and throws StaleObjectStateException.
--
-- That makes every entity editable exactly once per load, which is fatal for an
-- interactive form.  timestamp(3) stores what Hibernate sends.
--
-- Applied to a live database with:
--   docker exec -i envelope-db mariadb -ubudget -ptegdub envelope \
--     < sql/migrations/001-version-stamp-precision.sql
--
-- sql/bootstrap-mysql.sql carries the same precision for fresh installs; this
-- file exists only for databases created before it did.

alter table budgets            modify `stamp` timestamp(3) not null default current_timestamp(3);
alter table accounts           modify `stamp` timestamp(3) not null default current_timestamp(3);
alter table categories         modify `stamp` timestamp(3) not null default current_timestamp(3);
alter table transactions       modify `stamp` timestamp(3) not null default current_timestamp(3);
alter table allocations        modify `stamp` timestamp(3) not null default current_timestamp(3);
alter table allocation_presets modify `stamp` timestamp(3) not null default current_timestamp(3);
alter table users              modify `stamp` timestamp(3) not null default current_timestamp(3);
