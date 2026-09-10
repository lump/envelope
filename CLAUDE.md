# CLAUDE.md

Context for the **Envelope** budget app — a Java Swing desktop client that talks to a
servlet server over HTTP. Resurrected in 2026 to run on **JDK 25, MariaDB, and Tomcat 11**
(it was a 2007–2015 Ant/Ivy/Java-Web-Start project).

## What it is

An envelope budget: you pre-allocate funds into categories and can only spend what is
allocated. All account balances should reconcile to the real bank balance. See `README`.

## Architecture

Three source trees under `src/net/lump/`, split by who runs them:

| Tree | Runs in | What |
|------|---------|------|
| `envelope/shared/` | both | The wire protocol + data model. `command/Command` is the RPC unit; `entity/` are the Hibernate entities; `command/security/` is the challenge-response auth (`Challenge`, `Crypt`). |
| `envelope/client/` | Swing client | `portal/` = one RPC client per server domain (`SecurityPortal`, `HibernatePortal`, `TransactionPortal`); `ui/` = the Swing tree; `prefs/` = `LoginSettings`/`ServerSettings` (backed by `java.util.prefs`). |
| `envelope/server/` | Tomcat | `servlet/` — `InvocationServlet` handles `/invoke`, `DefaultServlet` (via `FileServer`) serves the landing page + static files, `ErrorServlet` handles `/error`; `dao/` = Hibernate `DAO` + `Security` (auth); `Controller` dispatches commands. |
| `lib/util/` | both | Shared utilities: `Encryption` (RSA/AES), `Base64`, `CipherOutputStream`, … |

**The RPC protocol is the load-bearing part.** The client Java-serializes a `Command` into
an HTTP multipart POST to `/invoke`; the server deserializes it, `Controller` dispatches by
`Command.Name`, and returns Java-serialized `Serializable` results.
**`org.hibernate.criterion.DetachedCriteria` is serialized across the wire** as part of this
protocol — which is why the app is pinned to Hibernate 5.6 (the last line that ships
`DetachedCriteria`). Moving to Hibernate 6/7 is not a dependency bump; it means redesigning
the protocol.

**Auth is a challenge-response, not a password over the wire.** The client generates an RSA
keypair and sends its public key in `getChallenge`; the server returns a `Challenge`
(encrypted to the client's public key) plus the server's public key; the client derives a
response from the password and encrypts it to the server's public key; the server compares it
to the stored hash. Passwords are stored as md5-crypt (`$1$`) via `Crypt`.

## Build & run

```
mvn package                          # → target/envelope.war + target/envelope-client.jar
cd docker && docker compose up -d    # MariaDB 11.8 + Tomcat 11
```

- Server: http://localhost:7041/envelope/  (health: `/envelope/info/ping` → `pong`)
- Client: `java -jar target/envelope-client.jar`
- Default logins (seeded by `sql/bootstrap-mysql.sql`): **admin / envelope**, **guest / guest**

The war is **bind-mounted** into Tomcat (not baked into an image), so a code change is
`mvn package` + `docker compose restart tomcat`.

## The modernization (and why the pins are where they are)

| Was | Now | Why |
|-----|-----|-----|
| `javax.servlet` / `javax.persistence` | `jakarta.*` | Tomcat 10+ / Jakarta EE 6.1 |
| Hibernate (old) | **5.6.15.Final `-jakarta`** | last line with `DetachedCriteria` (see above) |
| byte-buddy 1.12 (bundled) | **1.18.13** override | 1.12 can't read Java 25 class files |
| c3p0 | **HikariCP 7.1.0** | via `hibernate-hikaricp` with exclusions (never republished as `-jakarta`) |
| MySQL | **MariaDB 11.8** | `sql/bootstrap-mysql.sql` loads unchanged |
| log4j 1.2.15 | **log4j-1.2-api** (Log4j2) | code calls `org.apache.log4j.*` directly — no source change, CVE jar gone |
| Incors Alloy L&F | **FlatLaf** | Alloy's license key expired |
| Ant (`build.xml`) + Ivy | **Maven** (`pom.xml`) | — |

Other source changes forced by the JDK/library bumps: `MoneyType` rewritten as a `UserType`
(`ImmutableType` is gone in Hibernate 6); `AppleStuff` rewritten on `java.awt.Desktop`
(`com.apple.eawt` is gone); `DAO` moved to `Configuration` + `TransactionStatus` (Hibernate 5
API).

## Configuration

Server config is read from `DAO.properties` / `ServerPrefs.properties`, but **any key can be
overridden by a system property or env var** (`ServerPrefs.lookupOverride`): key
`hibernate.connection.url` on `DAO` → `-DDAO.hibernate.connection.url=…` or
`DAO_HIBERNATE_CONNECTION_URL=…`. The compose file sets these, plus `DAO_OK`/`SERVERPREFS_OK`
— the `.ok` flags mark each config section complete so the server doesn't hold every request
at the `/configure` form waiting for a human.

## Traps

- **The 2007 layout is kept.** `pom.xml` points `sourceDirectory` at `src/` (not
  `src/main/java`) and `testSourceDirectory` at `test/`. Don't "fix" it into the Maven
  convention.
- **Dead build files.** `build.xml`, `ivy.xml`, `ivysettings.xml`, `envelope.iml`,
  `envelope.ipr`, `slim_classes.sh`, and `one-jar/` are all superseded by `pom.xml`.
- **Dead JNLP / Java Web Start.** `server/servlet/jnlp/` (`Jnlp.java`, `jnlp.xml`),
  `FileServer.feedJnlp()`, and the `.jar.pack.gz`/pack200 branches are dead on JDK 11+. The
  client is now distributed as the fat JAR.
- **Tests need a live server + DB** and are skipped by default (`default-skip-tests`
  profile); run with `-DskipTests=false`.
- **Auth flaw (unfixed):** `Security.getChallenge()` persists the caller-supplied public key
  to the user row *before* any credential check, so an unauthenticated request can overwrite
  a user's stored public key — the same key `validateSession()` later uses to verify command
  signatures.