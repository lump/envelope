package net.lump.envelope.server.dao;

import org.hibernate.SessionFactory;

/**
 * What the users table must look like before the server takes a login.
 *
 * <p>Two ways a database can be wrong for 0.15, each turned into a sentence
 * rather than logins failing with nothing said: no SCRAM columns (migration 006
 * never applied), or users still holding md5-crypt hashes and no verifier -- a
 * database that was never started under 0.14, such as a pre-0.14 backup
 * restored.  0.14 converted those at startup; 0.15 no longer can, having dropped
 * the code with the column (migration 007).  Native SQL, since the entity no
 * longer maps crypt_password.
 *
 * @author troy
 */
final class SchemaCheck {

  private SchemaCheck() {}

  /** @throws IllegalStateException naming the migration or the release that puts it right */
  static void run(SessionFactory factory) {
    factory.inTransaction(session -> {
      Long scram = session.createNativeQuery(
          "select count(*) from information_schema.columns where table_schema = database()"
          + " and table_name = 'users' and column_name = 'scram_stored_key'", Long.class).uniqueResult();
      if (scram == null || scram.intValue() == 0)
        throw new IllegalStateException("the users table has no SCRAM columns -- apply sql/migrations/006-scram-verifiers.sql");

      Long legacyColumn = session.createNativeQuery(
          "select count(*) from information_schema.columns where table_schema = database()"
          + " and table_name = 'users' and column_name = 'crypt_password'", Long.class).uniqueResult();
      if (legacyColumn != null && legacyColumn.intValue() > 0) {
        Long unconverted = session.createNativeQuery(
            "select count(*) from users where crypt_password is not null", Long.class).uniqueResult();
        if (unconverted != null && unconverted.intValue() > 0)
          throw new IllegalStateException(unconverted + " user(s) still have an md5-crypt password and no SCRAM verifier"
              + " -- start 0.14.x against this database once to convert them, then apply sql/migrations/007-drop-crypt-password.sql");
      }
    });
  }
}
