package net.lump.envelope.server.dao;

import net.lump.envelope.shared.command.security.Scram;
import net.lump.envelope.shared.entity.User;
import org.apache.log4j.Logger;
import org.hibernate.SessionFactory;

import java.util.List;

/**
 * Turn every pre-0.14 password into a SCRAM verifier, at startup.
 *
 * <p>The users table held md5-crypt hashes, which the old login sent as they
 * were -- password-equivalents.  The new login feeds the md5-crypt of the
 * password into PBKDF2, so the server can compute each user's verifier from the
 * hash it already has ({@link Scram#fromMd5Crypt}) without anyone logging in or
 * changing a password, and then forget the hash.  Each conversion costs one
 * PBKDF2 at {@link Scram#ITERATIONS}, about half a second; once done there is
 * nothing left to convert and this finds no rows.
 *
 * <p>It needs migration 006's columns.  Without them the query fails, and
 * {@link DAO#initialize} reports the server not ready, naming the file.
 *
 * @author troy
 */
final class PasswordMigration {

  private static final Logger logger = Logger.getLogger(PasswordMigration.class);

  private PasswordMigration() {}

  /** @return how many users were converted */
  static int run(SessionFactory factory) {
    return factory.fromTransaction(session -> {
      List<User> legacy = session.createSelectionQuery(
          "from User u where u.cryptPassword is not null", User.class).list();
      int converted = 0;
      for (User user : legacy) {
        try {
          user.setVerifier(Scram.fromMd5Crypt(user.getCryptPassword()));
          converted++;
        } catch (Exception e) {
          // a malformed hash is a user who cannot log in either way; say who
          logger.error("could not convert the password of \"" + user.getName() + "\"", e);
        }
      }
      if (converted > 0) logger.info("converted " + converted + " md5-crypt password(s) to SCRAM verifiers");
      return converted;
    });
  }
}
