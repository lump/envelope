-- 006: SCRAM-SHA-256 verifiers for users (0.14).
--
-- Until 0.14 the login sent the stored md5-crypt hash itself, encrypted to the
-- server's key: capture it and you could open a session, and a copy of the users
-- table logged anyone in without cracking anything.  The login is now
-- SCRAM-SHA-256 (see Scram.java), and the server keeps a verifier -- a salt, an
-- iteration count, StoredKey and ServerKey -- none of which can be used to log
-- in.  md5_salt is the md5-crypt salt the client pre-hashes the password under,
-- which is what lets existing passwords convert without a reset.
--
-- These columns only make room.  The server fills them at startup
-- (PasswordMigration): each user still holding crypt_password gets a verifier
-- derived from it, and crypt_password is set to NULL.  Apply this BEFORE deploying
-- 0.14: until the columns exist, the server reports "not ready" naming this file.
--
-- Old backups still hold the md5-crypt hashes, which remain usable against a
-- pre-0.14 server and crackable at md5-crypt speed; change passwords, or retire
-- those backups, in time.
--
-- Applied to a live database with:
--   mariadb -h localhost -u budget -p envelope < sql/migrations/006-scram-verifiers.sql

alter table users
  add column if not exists md5_salt varchar(12) null after crypt_password,
  add column if not exists scram_salt varbinary(32) null after md5_salt,
  add column if not exists scram_iterations int null after scram_salt,
  add column if not exists scram_stored_key varbinary(32) null after scram_iterations,
  add column if not exists scram_server_key varbinary(32) null after scram_stored_key;
