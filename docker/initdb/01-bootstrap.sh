#!/bin/bash
# Runs once, on first init of an empty db-data volume.
#
# sql/bootstrap-mysql.sql stays the authoritative schema -- it is mounted at /sql
# and sourced verbatim rather than copied, so there is only one schema file to keep
# current.  It creates the database, the tables, the seed budget, and a `budget`
# user with the password baked into that file.
#
# The GRANT below then re-applies whatever credentials compose was given, so
# changing DB_USER/DB_PASSWORD in the environment works without editing the SQL.
set -euo pipefail

echo "envelope: loading /sql/bootstrap-mysql.sql"
mariadb --user=root --password="${MARIADB_ROOT_PASSWORD}" < /sql/bootstrap-mysql.sql

echo "envelope: granting envelope.* to ${MARIADB_USER}"
mariadb --user=root --password="${MARIADB_ROOT_PASSWORD}" <<SQL
create user if not exists '${MARIADB_USER}'@'%' identified by '${MARIADB_PASSWORD}';
alter user '${MARIADB_USER}'@'%' identified by '${MARIADB_PASSWORD}';
grant all on envelope.* to '${MARIADB_USER}'@'%';
flush privileges;
SQL

echo "envelope: bootstrap complete"
