# CLAUDE.md

Context for the **Envelope** budget app — a Java Swing desktop client that talks to a
servlet server over HTTP. Resurrected in 2026 to run on **JDK 25, MariaDB, and Tomcat 11**
(it was a 2007–2015 Ant/Ivy/Java-Web-Start project).

## What it is

An envelope budget: you pre-allocate funds into categories and can only spend what is
allocated. All account balances should reconcile to the real bank balance. See `README`.

## Architecture

Two top-level packages under `src/net/lump/`: `envelope/`, split three ways by who runs it,
plus a shared `lib/`.

| Tree | Runs in | What |
|------|---------|------|
| `envelope/shared/` | both | The wire protocol + data model. `command/Command` is the RPC unit; `entity/` are the Hibernate entities; `command/security/` holds `Challenge`, `Credentials`, `Crypt`, `Permission`. |
| `envelope/client/` | Swing client | `portal/` = one RPC client per server facet (`SecurityPortal`, `HibernatePortal`, `TransactionPortal`, over abstract `Portal`); `CriteriaFactory` builds every `DetachedCriteria` sent to the server; `ui/` = the Swing tree, with `ui/prefs/` holding `LoginSettings`/`ServerSettings` (backed by `java.util.prefs`). |
| `envelope/server/` | Tomcat | `servlet/` — `InvocationServlet` → `/invoke`, `DefaultServlet` → `/` (delegates to `FileServer`), `ErrorServlet` → `/error`; `dao/` = `DAO`, `Security`, `Generic`, `Action`; `Controller` dispatches commands. |
| `lib/` | mostly client | `Money.java` (the core value type, mapped by `entity/type/MoneyType`) plus `util/`. Only `Base64`, `Encryption`, `Interval` and `Revision` are used by the server; the rest (`EmacsKeyBindings`, `ByteFormat`, `Compression`, `ObjectUtil`, `ChildFirstClassLoader`) are client-only or unused. |

**The RPC protocol is the load-bearing part.** The client Java-serializes a `Command` into a
hand-rolled RFC 2388 multipart POST to `/invoke` (neither side uses the servlet `Part` API);
the body is optionally gzipped and AES-encrypted under an RSA-wrapped session key sent as a
separate `key` part. `Controller` dispatches by `Command.Name` — `getFacet()` picks the DAO
class, `name()` is the method. The **response is not multipart**: the server streams N
serialized objects with `Single-Object` / `Object-Count` / `Command-Sequence-Id` headers and
the client reassembles them into a list. Authentication rides in HTTP headers rather than in
the serialized payload, so the signature can cover a digest of the exact bytes on the wire and
the server can check it before anything reaches `readObject`.

**`org.hibernate.criterion.DetachedCriteria` is serialized across the wire** as a `Command`
parameter (`Command.Name.detachedCriteriaQueryList` / `…QueryUnique`), built by
`client/CriteriaFactory` and executed server-side by `dao/Generic`. That drags the whole
`org.hibernate.criterion` object graph onto the wire — `CriteriaImpl`, the `Restrictions`
`Criterion` impls, the `Projections` impls, `Order` — which is why the app is pinned to
Hibernate 5.6, the last line that ships those classes. Moving to Hibernate 6/7 is not a
dependency bump; it means redesigning the protocol.

**Login sends a password-equivalent, not a password.** The client generates an RSA keypair —
fresh on every launch, never persisted — and sends its public key in `getChallenge`. The
server's `Challenge` carries the server's public key and, as the "challenge", only
`Crypt.yankSalt(user.getCryptPassword())`: the salt from the stored hash, constant per password
and carrying no nonce. The client computes `Crypt.crypt(salt, password)`, reproducing the stored
md5-crypt hash, and RSA-encrypts it to the server key; the server decrypts and compares. Hashes
are md5-crypt (`$1$`), though `Crypt.crypt()` also dispatches to DES crypt for 2-/13-char
non-`$1$` salts.

**Everything after login is a session, not re-authentication.** `authChallengeResponse` mints a
session only once the hash verifies, binds it to the public key proven in that same handshake,
and returns the id encrypted to that key. Each later command carries `X-Envelope-Session`,
`X-Envelope-Stamp` and `X-Envelope-Signature` — the last being a signature over
`sessionId:stamp:digest(command bytes)`. `Security.validateSession` demands a live session, a
stamp within `Sessions.STAMP_WINDOW` (5 min), and a signature that is unspent and verifies
against the session's key. Since the client keypair dies with the process, the session id is
sender-constrained: capturing it is not enough to use it. Nothing in the database authenticates
anything — `users.public_key` is now vestigial.

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
| `javax.servlet` / `javax.persistence` | `jakarta.*` | Tomcat 10+; `jakarta.servlet-api` 6.1.0 (Jakarta Servlet 6.1) |
| Hibernate (old) | **5.6.15.Final `-jakarta`** | last line with `org.hibernate.criterion` (see above) |
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
- **Two different `lib/` directories.** The repo-root `lib/` (33 MB of vendored jars) and the
  Ant/Ivy build were deleted in `75461de` — Maven resolves all dependencies now. But
  `src/net/lump/lib/` is **live source**. Don't conflate them.
- **JNLP is wired up, not quarantined — and the client link is broken.** `server/servlet/jnlp/`
  (`Jnlp.java`, `jnlp.xml`), `FileServer.feedJnlp()` and the `.jar.pack.gz`/pack200 branches
  are all still routed: `FileServer` serves `/envelope.jnlp` (HTTP 200) and the landing page
  renders it as the **only** client link. It cannot work — Java Web Start was removed in JDK 11,
  and the JNLP points at `lib/client.jar`, which resolves to `WEB-INF/lib/client.jar` and
  **404s** because the war ships no such jar. The fat JAR is a local build artifact only; there
  is no server-side distribution mechanism yet.
- **Tests need a live server + DB** and are skipped by default (`default-skip-tests`
  profile); run with `-DskipTests=false`.
- **Auth is fixed at the command level; the login step still is not.** Commands are safe now
  (session-bound, command-bound, single-use signatures — see above). What remains is that the
  login exchange has no nonce, so the encrypted md5-crypt hash is a static password-equivalent:
  capture it and you can open a *new* session, and because the database stores that same value,
  a DB leak is directly usable without cracking. The fix is a nonce in `Challenge`.
- **The client cannot authenticate the server.** `LoginSettings.setServerKey()` is a bare
  assignment — whatever public key arrives in the `Challenge` is trusted, with no pinning and no
  CA. A MITM can hand the client its own key and collect the password-equivalent. App-level
  encryption is optional (`ServerSettings.getEncrypt()` defaults to **false**) and there is no
  TLS; today the only thing mitigating this is compose binding to `127.0.0.1`. Pin the server key
  on first use, or put TLS in front, before exposing this beyond localhost.
- **Sessions are per-JVM and in memory** (`Sessions`), so they die on redeploy and would need
  sticky sessions or a shared store if a second Tomcat ever appeared.
