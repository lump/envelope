-- 007: drop users.crypt_password (0.15).
--
-- crypt_password held md5-crypt hashes, which the pre-0.14 login sent as they
-- were: password-equivalents.  0.14 turned each into a SCRAM verifier at startup
-- and set the column to NULL, and since then nothing reads or writes it.  This
-- removes it, so no row can hold one again.
--
-- It refuses, changing nothing, while any user still has a hash -- a database
-- that has not yet been started under 0.14, whose users would otherwise be left
-- with no password at all.  Start 0.14.x against it once (after 006) to convert
-- them, then apply this.  From 0.15 the server no longer converts: restoring a
-- pre-0.14 backup means 006, a start under 0.14.x, then this.
--
-- Applied to a live database with:
--   mariadb -h localhost -u budget -p envelope < sql/migrations/007-drop-crypt-password.sql

-- the guard is one compound statement, so the client must not split it at its semicolons
delimiter //
begin not atomic
  if exists (select 1 from information_schema.columns
             where table_schema = database() and table_name = 'users' and column_name = 'crypt_password') then
    if (select count(*) from users where crypt_password is not null) > 0 then
      signal sqlstate '45000' set message_text =
        'users still hold md5-crypt passwords: start 0.14.x against this database once to convert them, then apply 007';
    end if;
    alter table users drop column crypt_password;
  end if;
end //
delimiter ;
