-- 002: scope the accounts unique index to the budget.
--
-- accounts carried `unique index name_type (name, type)` -- no budget column --
-- so account names were unique across the whole installation rather than within
-- a budget.  Two budgets could not both have a "Checking" Debit account, which
-- makes the multi-budget model the schema otherwise describes unusable, and it
-- is the wrong constraint anyway: nothing stops one budget holding two accounts
-- with the same name so long as their types differ.
--
-- (budget, name) is what categories already does one level down, where the index
-- is `unique index budget_name (account, name)`.
--
-- Applied to a live database with:
--   docker exec -i envelope-db mariadb -ubudget -ptegdub envelope \
--     < sql/migrations/002-account-name-unique-per-budget.sql

alter table accounts add unique index budget_name (`budget`, `name`);
alter table accounts drop index name_type;
