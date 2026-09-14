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

**The save unit is the Allocation, not the Transaction.** `Transaction.allocations` is
`@OneToMany(mappedBy = "transaction")` with **no cascade** — an inverse side that owns no
foreign key — so `saveOrUpdate(Transaction)` persists the transaction's own columns, succeeds,
and silently writes nothing for its allocations. The `PERSIST`/`MERGE` cascades live on
`Allocation`'s two `@ManyToOne` fields and point the other way, *up* to `Transaction` and
`Category`. So an edited allocation is saved on its own (`TransactionChangeHandler
.sendAllocationChange`), and saving an allocation is also what carries a new transaction into
existence. Every balance in the app — account totals, category totals, `Transaction
.getNetAmount()` — is a `sum(amount)` over allocations; there is no stored `transactions
.amount` column, so allocations are the authority for money and the transaction's amount field
is a target to reconcile against, not a source.

Adding and removing allocations follows from the same fact. An insert is just
`saveOrUpdate` on an `Allocation` whose id is null — its `@ManyToOne` cascades carry the
association — so no insert verb was needed. Delete is a bespoke
`Command.Name.deleteAllocation` on the `Action` facet rather than a generic "delete this
entity": nothing on the server checks whose budget a command touches, so a generic delete
verb would hand every authenticated caller the ability to remove any row of any type.
`Action.deleteAllocation` refuses to remove a transaction's **last** allocation, because a
transaction reaches its budget only through them. For the same reason
`Action.createTransaction` makes the transaction and its first allocation in one
server-side transaction — a transaction saved on its own, even for the gap between two round
trips, is a row no query can find — and `Action.deleteTransaction` clears the allocations
first, in that same database transaction, because their foreign key is `ON DELETE RESTRICT`
and the inverse side carries no cascade. That guard counts the siblings with a
**locking** read (`setLockMode(… PESSIMISTIC_WRITE)`): check-then-delete is otherwise three
separate statements, deleting a child never touches the parent's `@Version` so optimistic
locking offers nothing, and under `REPEATABLE READ` a plain `SELECT` would answer from the
snapshot taken before the caller waited. Two concurrent deletes on a two-allocation
transaction would each see two, each pass, and together strand it.

Accounts and categories are managed the same way, through `BudgetPortal` onto narrow
`Action` commands (`createAccount`, `renameAccount`, `deleteAccount`, and the three
`…Category` equivalents), reached by right-clicking the tree. Their delete guards
deliberately do **not** lock, unlike `deleteAllocation`'s: `allocations.category` and
`categories.account` are `ON DELETE RESTRICT`, so the foreign key is the real backstop and
the worst a lost race can do is fail the delete. The guards exist to turn a raw constraint
violation into a sentence. `deleteAllocation` had no such backstop — nothing in the database
stops a transaction reaching zero allocations — which is why that one check had to be made
atomic by hand.

**The tree's structure comes from the structure tables; only its money comes from
allocations.** `CriteriaFactory.getAccountTotals` and `getCategoriesForAccount` each run two
queries and merge them: the accounts (or categories) themselves, plus a `sum(amount)`
projection over `Allocation` keyed by id, defaulting to zero. They used to be a single
projection over `Allocation`, which inner-joins its way up to the account and therefore
omitted anything with no allocations yet — a newly created account was invisible in the tree,
and an invisible account cannot be given a category to make it appear. Balances stay derived;
only the list stopped being.

**An allocation preset is a set of rows sharing a name.** There is no header table: the
presets of a budget are the distinct `allocation_presets.name` values, and one with no rows
left has stopped existing — which is why creating one starts it with a row.
`Action.applyAllocationPreset` lays one over a transaction in a single command rather than a
save per row, because "Pay Day" expands to dozens of allocations that should all land or none.
A percentage is a **whole number** carried to many places (`7.60764626375748` means 7.6%), so
the product is taken at full precision and only the result brought to the cent; a row worth
nothing is skipped; and a row already on the transaction carrying no money is filled before a
new one is made, so the zero allocation a transaction is born with is used rather than
stranded. An **auto-deduct** row contributes a *pair* — the amount in and the same amount
straight back out — because that is what happened: the employee was paid and the money was
removed before it arrived. The pair nets to nothing on the account while keeping the payment
in the category's history. The result is not expected to balance; reconciling it against the
transaction amount is the user's job, with the imbalance panel showing the gap.
`forms/preset/AllocationPresetEditor` edits them, writing each change as it is made.

**The transaction amount field is a view of the allocations, not a field of its own.** There
is no `transactions.amount` column, so with exactly one allocation the two numbers are the
same thing and the form keeps them level in both directions — typing in the amount writes
through to that allocation, and editing the allocation moves the field. With more than one
there is a real decision about where a difference goes, which the form does not make: the
amount becomes a target, and the red imbalance panel shows the gap until the user apportions
it. `TransactionChangeHandler.syncAmountToBalance` detaches the field's own change listener
while it moves it, so the form catching up is never mistaken for the user typing.

**Every command declares the permission it needs, and `Controller.dispatch` checks it once.**
`Command.Name` carries a `Permission` bit — `READ` for looking, `WRITE` for changing money and
the shape it is filed into, `ADMIN` for users and budgets — and there is deliberately no
constructor that lets a session-required command omit it: one nobody thought about does not
compile. Dispatch loads the authenticated user into `ThreadInfo` (a `ThreadLocal`, cleared
afterwards since Tomcat reuses worker threads), checks `hasPermission` against the command's
bit, and refuses with `Permission_Denied` before the DAO method is even resolved. So a
READ-only user is refused every save, delete and create whatever the client draws, and the
DAO methods never have to check. `Action.requireAdmin()` remains on the user/budget commands
as belt-and-braces. `setPassword` needs only `READ` — anyone may set their own — and checks
self-or-ADMIN itself. Users go
over the wire as copies built by `Action.forClient`, carrying no password hash; a fresh object
rather than the loaded one with a field blanked, because blanking a field on an attached
entity is an update Hibernate would write. A new password is hashed **on the client**
(`AdminPortal.hash`, a fresh salt via `Crypt.generateNewMd5Salt`) exactly as the login exchange
hashes one, and only the hash travels. The Admin tab (`forms/preferences/AdminPanel`) asks
`whoAmI` on every refresh and draws accordingly — but what it draws is not what is enforced.

**The client is given a URL and uses whatever scheme it says.** `ServerSettings.setHostName`
accepts either `https://host[:port][/context]` or the old `host[:port]`; a URL sets scheme,
host, port (defaulting to the scheme's own, not 7041) and context, and a bare host means
http. `getCodeBase()` builds from the scheme and `HttpClient` builds `/invoke` from that, so
https is just a different URL — `HttpsURLConnection` is an `HttpURLConnection`. The server's
front page hands out the full URL to paste. **The Lumpnet private CA is bundled in the client**
(`client/tls/lumpnet-ca.crt`, a copy of `~/lump/config/keys/ca/pki/ca.crt`; replace and
rebuild when the CA rotates). `ClientTrust.install()`, called first thing in `Main`, sets a
default `SSLSocketFactory` whose trust manager accepts a chain if *either* the platform's CAs
or the bundled ones vouch for it — so a public certificate works, a Lumpnet-signed one works
on a machine that has never heard of Lumpnet, and an imposter is still refused. Two traps
inside it: a platform trust manager over an **empty** store (a stripped JRE, or a bad
`-Djavax.net.ssl.trustStore`) refuses with a `RuntimeException`, not a `CertificateException`,
so `EitherTrust` catches both; and `log4j1.compatibility` is a Tomcat flag, so a plain JVM
running client code logs nothing from log4j — do not read its silence as "didn't run". This
box has the Lumpnet CA in `/usr/local/share/ca-certificates/`, so it cannot demonstrate the
bundled path; test with `-Djavax.net.ssl.trustStore=<empty.jks>` to simulate a stranger's
machine. The settings test no longer ICMP-pings the host first — a
server behind a front end that drops ICMP is reachable and was reported as not.

**`Transaction.date` is a calendar day carried as midnight UTC.** The column is `DATE` and
means "August 31st" — no zone. But it crosses the wire as a `java.sql.Date`, which is an
instant, and the day an instant falls on depends on a zone. The server is zone-blind: it
stores the UTC day of whatever instant arrives and reads a day back as midnight UTC. So the
one representation both ends agree on is midnight UTC of the day, and `lib/util/Day` is the
only place a day is converted to or from the user's view: `Day.fromView` on the way out of the
chooser, `Day.toView` on the way in, `Day.today` for a new transaction. The user's zone is a
preference on the Server tab (`ServerSettings.getTimeZone`, applied as the JVM default so the
third-party chooser and the table's date column agree), defaulting to the machine's own. The
server and the image set **no** zone and must not: a pinned zone is right for one household
and wrong for the next. Before `Day`, the client made the instant at midnight in the user's
zone, and the round trip lost a day for every user not at UTC — west of Greenwich on the read,
east of it on the store — which the form's save-as-you-edit then wrote back. Nothing stored
needed migrating: the stored days were right, only the read was wrong.
(`hibernate.jdbc.time_zone` was tried and does nothing for `DATE` in 5.6.)

**Login sends a password-equivalent, not a password.** The client generates an RSA keypair —
fresh on every launch, never persisted — and sends its public key in `getChallenge`. The
server's `Challenge` carries the server's public key and, as the "challenge", only
`Crypt.yankSalt(user.getCryptPassword())`: the salt from the stored hash, constant per password
and carrying no nonce. The client computes `Crypt.crypt(salt, password)`, reproducing the stored
md5-crypt hash, and RSA-encrypts it to the server key; the server decrypts and compares. Hashes
are md5-crypt (`$1$`), though `Crypt.crypt()` also dispatches to DES crypt for 2-/13-char
non-`$1$` salts. `Crypt.PASSWORD_ENCODING` pins the password's byte encoding to
UTF-8 rather than taking the platform default, and the hash counts the password in bytes
rather than characters — both only matter outside ASCII, but without them the same password
hashed differently depending on the JVM's locale and matched no other md5-crypt
implementation. `Crypt.crypt` now agrees with `openssl passwd -1` for non-ASCII passwords as
well as ASCII ones.

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
cd docker && docker compose up -d    # Tomcat 11; the database is not containerised
```

- Server: http://localhost:7041/envelope/  (health: `/envelope/info/ping` → `pong`)
- Client: `java -jar target/envelope-client.jar`
- Default logins (seeded by `sql/bootstrap-mysql.sql`): **admin / envelope**, **guest / guest**

**The database is the native MariaDB on the host**, reached at `tiamat.lump:3306` and
overridable with `DB_URL`. There used to be a containerised one, bootstrapped on first start
by `docker/initdb/`; both were removed once the migrated budget moved to the host. Its data
volume is no longer declared and so no longer managed by compose — `docker volume ls` still
shows `envelope_db-data` until it is removed by hand. Note `tiamat.lump` resolves to
`127.0.1.1` on the host and to the host's LAN address from inside a container, so testing
that connection from a host shell says nothing about whether Tomcat can reach it.

The war is **bind-mounted** into Tomcat (not baked into an image), so a code change is
`mvn package` + `docker compose restart tomcat`. A client-only change needs neither — the
fat jar is rebuilt in place.

**For distribution, `./build.sh` builds a self-contained image** — a copy of
`~/lump/web/build.sh`, driven by `.image.name` (`lump/envelope`) and `.Dockerfile.version`,
tagging `registry.lump/lump/envelope:<branch>-<version>`. The `Dockerfile` is multi-stage: Maven
builds the war inside the image, so the host needs nothing but docker. It also adds Tomcat's
`RemoteIpValve`, since the config project fronts everything with HAProxy and without it the
front page would tell visitors to point the client at swarm-internal names.
The valve also reads `X-Forwarded-Port`; without it a proxied
request on anything but 443/80 is reported on the scheme's own port. `docker/swarm-stack.yml`
is the drop-in for `~/lump/config/stacks/envelope/source/`. Bump
`.Dockerfile.version` when the `Dockerfile` changes. `docker/compose.yml` remains the
bind-mounted development stack.

There is no system `mvn` on this box; the one that works is IntelliJ's bundled copy at
`~/bin/idea-IU-*/plugins/maven/lib/maven3/bin/mvn`, and it has to run **online** (`-o`
fails: `~/.m2` lacks the plugin versions that Maven build wants).

`bootstrap-mysql.sql` ends with `grant all on envelope.* to budget identified by 'tegdub'` —
a **development-stack** convention only. On the swarm, the config project bootstraps and
grants empty databases itself with its own encrypted credentials, and the image reads them
from `DAO_HIBERNATE_CONNECTION_*`; `tegdub` is never right there.

Schema changes go in `sql/migrations/` **and** in `sql/bootstrap-mysql.sql` — the bootstrap
is the authoritative schema for a fresh volume (sourced by `docker/initdb/01-bootstrap.sh`),
and it opens with `drop database if exists envelope`, so never run it against a live
database. To syntax-check an edit to it, rewrite the database name and load it into a
throwaway schema.

`sql/migrate.pl` is a 2010 one-off that pulls a legacy `budgets` database into this schema.
It is not part of the build and is kept for the mapping it documents. Its connection details
come from `MIGRATE_SRC_*` / `MIGRATE_DST_*` environment variables; only
`MIGRATE_SRC_PASSWORD` has no default, being the only one that reaches another machine.

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
- **The client is distributed from the server's front page.** `mvn package` builds the fat
  jar in `prepare-package` and the war plugin copies it to `WEB-INF/client/envelope-client.jar`
  — under `WEB-INF` so Tomcat never lists it, but deliberately **not** under `WEB-INF/lib`,
  which is the webapp classpath (a 31 MB fat jar there would load a second Hibernate into
  the server). `FileServer.feedClient` hands it out at `/envelope-client.jar`, and the landing
  page gives run instructions naming the host the visitor reached. All the Java Web Start
  machinery — `Jnlp.java`, `jnlp.xml`, `feedJnlp`, the pack200 branches, `/lib/*` and `/us/*`
  serving of `WEB-INF/lib`, `/info/security.policy`, `/log4j.properties` — was removed in one
  go; Web Start left with JDK 11. The client only ever calls `/invoke`.
- **`@Version` needs sub-second stamp columns.** Every versioned entity maps `@Version` onto a
  `stamp` column. Declared as plain `timestamp` (whole seconds) the version Hibernate hands
  back to the client can never match the value the row holds, so the *first* save of an entity
  succeeds and the second is refused with `StaleObjectStateException` — every entity editable
  exactly once per load. `sql/migrations/001-version-stamp-precision.sql` moved the live tables
  to `timestamp(3)`; keep any new versioned table at `timestamp(3)` too.
- **Account names are unique per budget, but only since migration 002.** `accounts` shipped
  with `unique index name_type (name, type)` — no budget column — so account names were unique
  across the whole installation and two budgets could not both have a "Checking" account.
  `sql/migrations/002-account-name-unique-per-budget.sql` re-scopes it to `(budget, name)`,
  matching what `categories` already does one level down with `(account, name)`.
- **Category names are unique only within an account, and the picker completes by
  prefix.** Once a budget has two accounts, a "Computer" in each is a real situation. The
  allocation category combo matches what is typed against `toString()` and takes the **first**
  hit in list order, so with a bare-name `toString()` and a name-ordered list, every typed
  prefix resolved into whichever account sorted first — a transaction started under one
  account silently moved to another. Two things now hold that off: `Category.toString()`
  qualifies the name with its account as `Account/Name`, both completers also answer to the bare name, and `TransactionForm.categoryOrderFor` lists the
  transaction's own accounts first. Don't put the bare name back in `toString()`.
- **A user has exactly one budget.** `User.budget` is a single `@ManyToOne`, and
  `CriteriaFactory.getBudgetForUser` reads it as a unique result into `State.budget`, which the
  tree roots on. The `budgets` table holds many, and accounts/categories/presets are all scoped
  to one — but nothing lets a user reach a second. Separate banks live as separate *accounts*
  inside the one budget; a second budget would be a separate envelope system, and supporting
  that per-user needs a join table, a budget picker, and `State` to track a current budget.
- **The tree rebuilds nodes by position, so a node's children can outlive its contents.**
  `Hierarchy.updateChildren` reuses the node at each index and swaps its user object, which
  means inserting an account (they are listed by name) shifts every later account onto a
  different node. Three bugs lived here and are fixed: an empty child list returned early
  instead of clearing, so deletions left stale rows and a brand-new account inherited the
  categories of whoever previously held its position; surplus nodes were removed by ascending
  index while the list shifted down, taking out every other one; and a reused node kept its old
  children. A node whose object is replaced now drops its children first.
- **A new transaction is dated today, and the list is date-filtered.** "New Transaction"
  (right-click the transaction list) creates a zero-amount stub in the selected category and
  opens it on the form, which is the entry screen — every field saves as it is edited. But
  `TableQueryBar`'s begin/end date choosers filter the list, so if the range excludes today the
  new row will not appear in it even though the form is editing it.
- **Ids are `IDENTITY`, not `AUTO`.** Every table is `auto_increment`, but the entities were
  annotated `@GeneratedValue(strategy = AUTO)`, which under Hibernate 5 resolves to a
  *sequence* and fails with `Unknown SEQUENCE: 'hibernate_sequence'`. That went unnoticed for
  the whole resurrection because nothing in the app ever inserted a row — it was read and
  update only — so the first `INSERT` ever attempted was the first thing to hit it. All ten
  entities now declare `IDENTITY`. Don't "simplify" them back to `AUTO`.
- **Nothing serializes two saves of the same row.** `ThreadPool` is
  `(0, Integer.MAX_VALUE)` over a `SynchronousQueue`, so every submitted task starts
  immediately on its own thread. Two quick edits to an allocation that has not been inserted
  yet would both see a null id and insert it twice — demonstrated, two rows.
  `sendAllocationChange` is therefore single-flight per allocation, re-sending once if the row
  changed while its save was in the air. Anything else that saves per-keystroke needs the same
  treatment.
- **`Transaction.equals` does not compare allocations, and must not be "fixed" naively.** It
  builds both sides of that comparison from `this.allocations` (the second one is unqualified),
  so it compares the list to itself and the check always passes — an allocation change is
  invisible to it. Repointing it at `that.allocations` makes `Transaction.equals` and
  `Allocation.equals` mutually recursive (the latter compares its `transaction`), which is a
  `StackOverflowError` for any two transactions whose scalar fields match. The self-comparison
  bug is the only thing preventing that today. Track allocation edits explicitly instead.
- **Running the test suite rewrites the real client settings.** `TestSuite`'s static
  initializer calls `ServerSettings.setHostName(localHost() + ":8080")` and
  `LoginSettings.setUsername("bowmantest")`, and those go straight into the same
  `java.util.prefs` store the actual Swing client reads (`~/.java/.userPrefs/net/lump/...`).
  So `-DskipTests=false` silently repoints your client at `<hostname>:8080` and leaves it
  there. Put the settings back afterwards (`localhost:7041`, context `/envelope`) or the
  client — and every probe — fails with `ConnectException` and a settings dialog.
- **The allocation table re-signs a committed cell under the *current* view.** It shows an
  expense unsigned and negates what the editor hands back according to `expense` at commit
  time. The Expense/Income radio both flips that view (synchronously, via
  `TransactionChangeHandler.setExpense`) and takes focus from any open editor (committing it a
  moment later via `terminateEditOnFocusLost`) — in that order, so a `25.00` typed into an
  expense row committed under the Income view as `+25.00`, a silent sign flip, and then raced
  the amount field's own focus-loss save for the same row, which surfaced as
  `StaleObjectStateException`. `setExpense` in both the handler and the model now commits any
  open editor **before** the view changes. If you add another way to change the view, do the
  same.
- **Tests need a live server + DB** and are skipped by default (`default-skip-tests`
  profile); run with `-DskipTests=false`. The suite does not currently pass: it hangs (some
  tests block on Swing dialogs), and `TestMoney.testPrint` fails outright — `Money`'s
  constructor rounds `HALF_UP` while its `toString()` and its own Javadoc promise `HALF_EVEN`,
  so the constructor has already destroyed the half-fraction. Pre-existing since `b4dfb3c`,
  unrelated to the resurrection.
- **A database failure used to surface as a bare `NullPointerException`.** `DAO.initialize`
  swallowed `buildSessionFactory()`'s exception, leaving the factory null; every request then
  died in `getCurrentSession()` with nothing in the message, while the real reason — on the
  swarm, `Access denied for user 'budget'` — sat one line in the startup log as an unhelpful
  `ServiceException`. It now keeps the root cause: the log says
  `could not build the session factory -- <cause> (url …)` and requests fail with `the
  database is not available: <cause>`. `/info/ping` is **liveness only** — it never touches
  Hibernate, so a container sat "healthy" for hours with no database. `/info/ready` opens a
  session and runs a query (`503 not ready: <cause>` otherwise); the image, dev compose and
  swarm health checks all use it. `InvocationServlet` is `load-on-startup`, so a bad database
  shows at startup rather than at the first user's login.
- **`mvn package` rewrites the bind-mounted war under a running Tomcat**, which triggers a
  reload; requests landing mid-reload fail with `IllegalStateException: this web application
  instance has been stopped already` while the health check still answers `pong` (it never
  touches the database). Always `docker compose restart tomcat` after packaging, even for a
  client-only change — the war is rebuilt either way.
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
- **Permissions say what a user may do, not to which budget.** `READ`/`WRITE`/`ADMIN` are
  enforced per command at dispatch, but no data command is scoped to the caller's budget — a
  WRITE user of one budget can still query or save another budget's transactions via
  `DetachedCriteria`. Scoping writes is tractable (resolve the entity's budget and compare);
  scoping reads means rewriting or replacing the client-built criteria.
- **Sessions are per-JVM and in memory** (`Sessions`), so they die on redeploy and would need
  sticky sessions or a shared store if a second Tomcat ever appeared.
