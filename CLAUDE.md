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
| `envelope/shared/` | both | The wire protocol + data model. `command/Command` is the RPC unit and `command/WireFilter` the classes one may contain; `entity/` are the Hibernate entities; `query/Total` is what the tree's balances arrive as; `command/security/` holds `Challenge`, `Credentials`, `Crypt`, `Permission`. |
| `envelope/client/` | Swing client | `portal/` = one RPC client per server facet (`SecurityPortal`, `QueryPortal` for every read, `TransactionPortal`, `BudgetPortal`, `AdminPortal`, over abstract `Portal`); `ui/` = the Swing tree, with `ui/prefs/` holding `LoginSettings`/`ServerSettings` (backed by `java.util.prefs`). |
| `envelope/server/` | Tomcat | `servlet/` — `InvocationServlet` → `/invoke`, `DefaultServlet` → `/` (delegates to `FileServer`), `ErrorServlet` → `/error`; `dao/` = `DAO` (with the whose-budget checks), `Security`, `Query` (reads), `Action` (writes); `Controller` dispatches commands. |
| `lib/` | mostly client | `Money.java` (the core value type, mapped by `entity/type/MoneyConverter`) plus `util/`. Only `Base64`, `Encryption`, `Interval` and `Day` (the readiness probe's date check) are used by the server; `Revision` reports the application version and is read by the client's `AboutBox`; the rest (`EmacsKeyBindings`, `ByteFormat`, `Compression`, `ObjectUtil`, `ChildFirstClassLoader`) are client-only or unused. |

**The RPC protocol is the load-bearing part.** The client Java-serializes a `Command` into a
hand-rolled RFC 2388 multipart POST to `/invoke` (neither side uses the servlet `Part` API);
the body is optionally gzipped and 3DES-encrypted (`DESede/ECB/PKCS5Padding`, no IV) under an
RSA-1024-wrapped session key, signed `SHA1withRSA`, sent as a separate `key` part. (This was
documented as "AES" for a long time, which is why the 2007-grade algorithms went unaudited.) `Controller` dispatches by `Command.Name` — `getFacet()` picks the DAO
class, `name()` is the method. The **response is not multipart**: the server streams N
serialized objects with `Single-Object` / `Object-Count` / `Command-Sequence-Id` headers and
the client reassembles them into a list. Authentication rides in HTTP headers rather than in
the serialized payload, so the signature can cover a digest of the exact bytes on the wire and
the server can check it before anything reaches `readObject`.

**Every command is a named question or change about the caller's own budget, and its
parameters are plain values.** Until 0.12 the client built Hibernate `DetachedCriteria` in
`client/CriteriaFactory` and shipped them to `dao/Generic`, which ran whatever arrived, beside
generic `get`/`load`/`save`/`saveOrUpdate` of any entity. Any logged-in user could therefore
read every row of every table — `User.cryptPassword` included, which is a password-equivalent
(see the login trap below) — and insert a `User` with ADMIN permission; both were demonstrated
as guest against a throwaway stack before the fix. Now reads are the `Query` facet
(`myBudget`, `categories`, `accountTransactions`, `categoryBalances`, …, reached through
`QueryPortal`) and edits are `Action`'s `saveTransaction` / `saveAllocation` / `savePreset`
beside the existing narrow verbs. The criteria moved to `dao/Query` unchanged in 0.12, and in
0.13 became HQL for the same joins, filters, grouping and ordering; both times an
old-versus-new diff of every read matched, on the seed data and on the dev database's guest
budget. The budget is never the client's to say: it comes from the
authenticated user, and every id a command names is checked against it (`DAO.requireMy…`)
before anything else runs. **Don't add a generic verb back** — the server cannot scope what it
cannot see the purpose of.

**The saves take values, never an entity.** `saveTransaction(id, stamp, date, entity,
description, reconciled, transfer)`, `saveAllocation(id, stamp, transactionId, categoryId,
amount)` and `savePreset(id, stamp, name, categoryId, allocation, type, autoDeduct)`: the server
loads its own row, refuses a stamp that is not the stored one (`Action.requireCurrent`, the
same answer `@Version` gave as `StaleObjectStateException`), and copies the fields across; the
portals still take entities and unpack them, so no call site changed. The first cut of 0.12
took entities and `saveOrUpdate`d them, and an independent review read another budget through
it: a `Transaction` sent to `saveTransaction` carried a `PersistentBag` whose collection key the
client had set to a foreign transaction id, and reattaching it loaded that collection into the
reply. **Never reattach a client's object graph** — it hands Hibernate whatever was put in it.
An existing allocation also cannot move to another transaction (`saveAllocation` refuses), which
would otherwise take a transaction's last allocation around `deleteAllocation`'s guard.

**What a command may contain is an allowlist.** `InvocationServlet` reads every command
through `WireFilter.COMMANDS`, an `ObjectInputFilter` that admits `Command` and its `Name`,
`Money`, the JDK value types (boxed primitives, `BigDecimal`/`BigInteger`, the three date types,
the `ArrayList` holding the parameters), and `KeyRep` plus the RSA key it resolves to for login
— **no entity and no Hibernate class** — and refuses everything else with a 400 *before*
constructing it, so a gadget chain never starts. Depth, references, array length and bytes are
capped tight, since a command is a handful of values. Two non-obvious entries:
`java.lang.Object`, because `ArrayList.readObject` checks its backing `Object[]` against the
filter; and `sun.security.rsa.RSAPublicKeyImpl`, because the filter is asked again about what
`KeyRep.readResolve` returns. A new parameter type needs a line there (the refusal is logged with
the class name); an entity parameter means send its id and the fields instead. `Money` also
refuses, in its own `readObject`, a `BigDecimal` with an absurd scale or precision
(`Money.isSane`) — a few bytes that would turn the first division into an allocation of
billions of digits — and `savePreset` applies the same check to its bare `BigDecimal`. Ahead of
the filter, the servlet caps each request part and the inflated command at 1 MB: both are read
before the signature can be checked (it covers the inflated bytes), and the inflation used to be
uncapped and quadratic — 16 KB of gzip with no credentials held a thread for four seconds.
`Controller` resolves a command only to a method its facet class **declares**, so a command
sharing a name with one of `DAO`'s public generic methods (`delete`, `merge`, `update`, …) is
refused rather than handed the generic verb. The client does not filter what the server sends
back — it trusts its server (see the server-authentication trap below).

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
`saveAllocation` on an `Allocation` whose id is null — its `@ManyToOne` cascades carry the
association — so no insert verb was needed. Delete is a bespoke
`Command.Name.deleteAllocation` on the `Action` facet rather than a generic "delete this
entity", like every write: each is one type, in the caller's budget.
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
allocations.** `Query.accountTotals` and `categoryTotals` each run two
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
`forms/preset/AllocationPresetEditor` edits them, writing each change as it is made. Its
Target Amount is the transaction amount the preset would be applied to, held only on
screen: the editor values every row against it with `AllocationPreset.valueFor` -- the
same call `applyAllocationPreset` makes, so the two cannot drift -- and shows the
Incoming / Sum / Outgoing line and imbalance warning through `components/AllocationTotals`,
which the transaction form also uses for its icons and warning.

**The allocation table's Balance and Projection columns are computed, and "Balance"
means the category as of the transaction's day, without the transaction.** Each row shows
`Category | Balance | Allocation | Projection`. Balance is the category's `sum(amount)` over
allocations whose transaction is dated **on or before this one's day** (`le("t.date", …)`
against `Transaction.date` in its wire form, so same-day transactions count) with **this
transaction's rows excluded on the server** (`ne("t.id", …)`), both in
`Query.categoryBalances(transactionId, asOf)`; Projection is that plus
every row of this transaction in the same category as they stand on screen, unsaved edits
included. So an old transaction shows the balance it met and the balance it left, not
today's less itself; an auto-deduct pair projects to the balance it leaves rather than one
row up and the other down; and a brand-new transaction's Balance is exactly what the tree
shows. Excluding the transaction is what makes the number safe to read at any moment: the
form saves per keystroke, and a plain sum would count a row or not depending on which round
trip won. The read happens three ways: one grouped query for the whole budget in
`TransactionChangeHandler.importNew` (off the EDT, before `setFormData`; a preset re-enters
there too) because a transaction arrives with all its rows at once; the same grouped query
again when the **date field** is edited (`rereadBalances`, from the date's save runnable),
since every balance is as of that day; and one single-category query (`refreshBalance`, via
`EditListener.categoryChosen`) each time a category is put on a row by hand, since those
arrive seconds apart and the upfront read may be minutes old. A read is applied only if the
form is still on the same transaction **and the same day** (`stillCurrent`) — otherwise a
newer read is already on its way. `MoneyRenderer` sets the computed cells on the panel's
grey so they do not read as inputs.

**The allocation table only ever lands on an editable cell.** The `changeSelection`
override in `TransactionForm.createUIComponents` redirects any move that would arrive on
Balance or Projection: Tab carries on to the right (Category → Allocation → next row's
Category, adding an empty row past the last), Shift-Tab to the left, and a click goes to the
nearest editable column with Allocation winning a tie. Column reordering is off, because
`setFormData` sizes the three money columns by index. The old wrap-to-`(0,0)`-on-Tab check is
still there for the first Tab into an empty lead. `setFormData` also commits any editor
still open **before** swapping the model's rows: left to `setExpense` to stop it afterwards,
it wrote into the new list at its old row index, which a shorter transaction did not have.

**The transaction list keeps up with the form, as the tree does.** After each save the form
makes — its own fields (`sendChanges`), or an allocation (through `refreshTotals`, the same
hook that re-reads the tree) — `TransactionChangeHandler.reflectInTable` hands the form's
transaction to `TransactionTableModel.reflect`, which rebuilds that transaction's rows from it
as a refresh would show them: the net on the viewed account (one row), or one row per saved
allocation in the viewed category in allocation-stamp order (rows now keep their allocation
id, past the visible columns, which the fill used to drop); in place for an allocation edit,
last within its day after an own-field save (the query orders a day by transaction stamp);
running Balance/Reconciled and the in/out totals recomputed from the beginning balances.
Dated before the range, or **joining** a table it was not in, it falls back to a full re-read —
the client cannot tell whether the transaction's money was sitting in the beginning balance,
and the first cut double-counted one coming back into the range. While a re-read is filling
it stands aside. The swap sets `isQuiet()`, which the form's selection listener honours: the
selection passing through other rows would otherwise queue loads of them and switch the form
away mid-edit. The refresh button is unchanged. Checked against the oracle — after each saved
change, the reflected table equals a freshly loaded one, in both views, for edits, reorders,
pairs, moves between accounts and in and out of the range.

**And the list's reconciled checkbox reaches the form.** The checkbox writes straight to the
server (`updateReconciled`), which moves the transaction's stamp; the form, holding the
transaction with the old stamp and old flag, then had its next save refused as stale (before
0.12, accepted — putting reconciled back). `updateReconciled` now returns the new stamp, and
the table hands it with the flag to `TransactionForm.noteReconciled`. Clicking the box also
selects the row, so there are two cases. Open in the form already — the usual one, reconciling
what you are looking at: the form re-selects without reloading, and `adoptReconciled` puts
flag and stamp on its copy (and on `pristine`, so it is not mistaken for an edit) — and
re-applies `lockAmountIfReconciled`, since a reconciled transaction's Amount field is locked
and an unreconciled one's is not. That lock used to be set only when the form was filled, so
a tick under an open form left the field as it was (0.13.4). Opening as
you click: the load reads the transaction and makes two more round trips before installing it,
so the write can land after its read. `noteReconciled` records each write per transaction, and
the load looks twice — after its read, and again **once installed, on the event thread**, where
the note also runs, so one of the two always sees the other; a check only after the read let
the note slip past while the form still showed the previous transaction (caught on the real
UI). Checked by driving the real client on Xvfb with `java.awt.Robot` — click the row's box,
type into the form's description — against a control: the 0.13.2 client failed the first round
("changed since it was read"); this one passed every round, both cases. Such a harness must
not touch the list's date range: it is remembered in the real client's `java.util.prefs`, not
the throwaway node, and changing the choosers saves it.

**A new transaction opens on Expense.** `setFormData` derives the radio from the sign of
the net amount, and a fresh transaction is a zero stub, so zero has to count as expense
(`<= 0`, in both `setFormData` and `isExpense()`) — most transactions are.

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
preference on the Server tab (`ServerSettings.getTimeZone`, applied as the JVM default for
the third-party chooser's benefit), defaulting to the machine's own. It is **not** what makes
the table's date column agree with the chooser -- that claim was here and was wrong. The
column had no renderer registered, so it fell through to Swing's `DateRenderer`, which
formats in the default zone: midnight UTC is the evening before west of Greenwich, so the
list read a day early for a user in the Americas while the form's field, fed `Day.toView`,
read correctly. `forms/transaction/DayRenderer` formats the stored instant in **UTC**, which
is the zone its day is expressed in, so the cell says the day the column means for every
reader and does not move when the zone preference does. Don't "fix" it to use `Day.toView`;
only the chooser needs that, because only the chooser thinks in instants. The
server and the image set **no** zone and must not: a pinned zone is right for one household
and wrong for the next. Before `Day`, the client made the instant at midnight in the user's
zone, and the round trip lost a day for every user not at UTC — west of Greenwich on the read,
east of it on the store — which the form's save-as-you-edit then wrote back. Nothing stored
needed migrating: the stored days were right, only the read was wrong.
The date pickers use `ZonedDateEditor`: the stock jcalendar editor captures the
default zone in its `SimpleDateFormat` at construction and never looks again, so after a zone
change on the Server tab the text field showed the 30th while the popup said the 31st. And
when the zone changes, on-screen pickers are re-set from their **source of truth** — the
entity's stored day — never from themselves: a picker's `getDate()` is an instant, and
re-setting that instant under a new default zone reinterprets it as a different day, one day
further off per switch (`08/27` after five). `TransactionChangeHandler.showDate` is the
right way to put a stored day on the picker.
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
`~/lump/web/build.sh`, driven by `.image.name` (`lump/envelope`) and the three version
files, tagging `registry.lump/lump/envelope:<branch>-<major>.<minor>.<patch>`. The `Dockerfile` is multi-stage: Maven
builds the war inside the image, so the host needs nothing but docker. It also adds Tomcat's
`RemoteIpValve`, since the config project fronts everything with HAProxy and without it the
front page would tell visitors to point the client at swarm-internal names.
The valve also reads `X-Forwarded-Port`; without it a proxied
request on anything but 443/80 is reported on the scheme's own port. `docker/swarm-stack.yml`
is the drop-in for `~/lump/config/stacks/envelope/source/`. Bump
the patch at least when the `Dockerfile` changes. `docker/compose.yml` remains the
bind-mounted development stack.

**The application version lives in `.Major.version`, `.Minor.version` and
`.Patch.version`**, one number each, joined with dots — currently **0.13.4**. They are the
single source of truth and are read twice: `build.sh` assembles the image tag from them,
and `pom.xml` reads them (via `maven-antrun-plugin`, because plain Maven cannot read a file
into a property) into the filtered `lib/util/revision.properties`, which `Revision` reads at
runtime. So the artifact and the image it ships in always agree. `.Dockerfile.version` was
the old single-file name; `build.sh` still falls back to it, which is what keeps the script
a drop-in for the other projects that share it — **don't diverge `build.sh` from
`~/lump/web/build.sh` in any way that breaks that fallback.** `.dockerignore` lets exactly
those three files into the image context, since there is no `.git` in there for anything to
read. Commit and branch are recorded only if the build is passed `-Denvelope.commit` and
`-Denvelope.branch`; unset, `Revision` reports the version alone rather than guessing.

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
| Hibernate (old) | **7.4.16.Final** (`org.hibernate.orm`, Jakarta Persistence 3.2) | the newest stable line. Hibernate names no community LTS — the latest series is the maintained one and the previous drops to limited support when the next ships — so stay current. 5.6 (end-of-life) was pinned until 0.12 by `DetachedCriteria` on the wire; 0.13 moved |
| byte-buddy 1.12 (bundled) | byte-buddy 1.18 (bundled by 7.4) | the override 5.6 needed for Java 25 class files is gone |
| c3p0 | **HikariCP 7.1.0** | via `hibernate-hikaricp`, pinned above the version it asks for |
| MySQL | **MariaDB 11.8** | `sql/bootstrap-mysql.sql` loads unchanged |
| log4j 1.2.15 | **log4j-1.2-api** (Log4j2) | code calls `org.apache.log4j.*` directly — no source change, CVE jar gone |
| Incors Alloy L&F | **FlatLaf** | Alloy's license key expired |
| Ant (`build.xml`) + Ivy | **Maven** (`pom.xml`) | — |

Other source changes forced by the JDK/library bumps: `AppleStuff` rewritten on
`java.awt.Desktop` (`com.apple.eawt` is gone); `DAO` on `Configuration` + `TransactionStatus`.

What the Hibernate 7 move (0.13) changed, and the traps it sprang:
- `Query` is HQL (the criteria API is gone since 6); `DAO`'s helpers are the Jakarta
  Persistence verbs (`find`, `getReference`, `persist`, `remove`), and the unused generic ones —
  list by `Criterion`, saveOrUpdate of anything, identifier access by `ClassMetadata` — were
  deleted. `Identifiable.getId/setId/getStamp` are abstract: their defaults went through
  `ClassMetadata`, from a class the client loads. `finalize()`, which committed on the
  finalizer thread, is gone.
- `MoneyType` (a `UserType`, whose interface moved every major version) is
  `MoneyConverter`, a standard `AttributeConverter`. `Query.money` takes a `sum()` as either
  `Money` or `BigDecimal`. `@org.hibernate.annotations.Entity(dynamicUpdate)` is
  `@DynamicUpdate`; `@org.hibernate.annotations.Table` went. The dialect is plain
  `MariaDBDialect`; the versioned names went with 6.
- **Hibernate 7 cascades `PERSIST` at flush**, as Jakarta Persistence says, where 5 under
  native bootstrap did not. `createTransaction` detached the new transaction to return it
  while the new allocation, cascading `PERSIST` up to it, stayed managed — the commit failed
  with "detached entity passed to persist" *after the reply said it worked*. `DAO.evict` now
  flushes and clears the whole session; it is only ever a command's last act.
- `ScrollableResults.get()` returns the row, not always an array (`Controller.row`), and
  `PersistentBag` moved to `org.hibernate.collection.spi`, so a 0.12 client cannot read a 0.13
  server's replies.
- **The build has no deprecated calls, and says so if one appears** (`-Xlint:deprecation` and
  `-Xlint:removal` in `pom.xml`, since 0.13.1). Clearing them meant: every query typed
  (`createSelectionQuery(hql, Type.class)`, `createNativeQuery(sql, Type.class)`); the locking
  reads on `setHibernateLockMode(LockMode.PESSIMISTIC_WRITE)` rather than an alias — each
  selects `Allocation` alone, so it is the same `SELECT … FOR UPDATE` on the same rows (checked
  in the query log, and by racing two `deleteAllocation`s 25 times: one through every time);
  the row count from `getPosition()`, which counts from 1, where `getRowNumber()` counted from
  0; `Class.newInstance()` replaced by the constructor's `newInstance()` with its
  `InvocationTargetException` unwrapped, so "the database is not available: …" still reaches
  the readiness probe as itself; and on the client the `*_DOWN_MASK` key masks (`KeyStroke`
  normalizes old and new to the same stroke), `URI`-built server URLs (byte-identical to the
  old ones), and `RoundingMode` in `Money`, whose unused int-rounding overloads went.

## Configuration

Server config is read from `DAO.properties` / `ServerPrefs.properties`, but **any key can be
overridden by a system property or env var** (`ServerPrefs.lookupOverride`): key
`hibernate.connection.url` on `DAO` → `-DDAO.hibernate.connection.url=…` or
`DAO_HIBERNATE_CONNECTION_URL=…`. The compose file sets these, plus `DAO_OK`/`SERVERPREFS_OK`
— the `.ok` flags mark each config section complete so the server doesn't hold every request
at the `/configure` form waiting for a human.

**A default changed in `DAO.properties` reaches existing installs only since 0.13.**
`ServerPrefs` copies every default into the `java.util.prefs` store on first start, and the
stored copy is what is read. It used to win for good, so a long-lived install kept whatever
it first started with — the Hibernate 7 upgrade renamed the dialect, and the dev container
(restarted, never recreated) went on asking for `MariaDB106Dialect` and never came up. Now a
`seeded-defaults` child node records which default each pref was seeded from; while the pref
still holds exactly that, startup moves it to the current default, and a value someone changed
through `/configure` stays. Installs from before the record keep what they hold (recreate the
container, or clear `~/.java/.userPrefs/net/lump/envelope/server` in it). Swarm containers are
recreated on every deploy and start empty, so they always had the current defaults.

## Traps

- **There are no `@version` tags in the source, and adding one back is a mistake.** 69 files
  carried `@version $Id: Foo.java,v 1.23 2010/09/22 ... $`, frozen since CVS: git has no
  keyword expansion and will not grow one, because a file's bytes are an input to the commit
  hash. (`ident` expands `$Id$` to the *blob* hash — content only, no commit, author or date
  — and `export-subst` substitutes real commit data but only into `git archive` output, and
  only the archive's own commit rather than each file's.) `git log -1 --format='%h %an %cI'
  -- <file>` answers the question and cannot go stale. The two `@version` lines that remain,
  in `lib/util/Base64.java` and `forms/transaction/CellEditor.java`, are upstream's (Josh
  Bloch's and Sun's) and stay.
- **The 2007 layout is kept.** `pom.xml` points `sourceDirectory` at `src/` (not
  `src/main/java`) and `testSourceDirectory` at `test/`. Don't "fix" it into the Maven
  convention.
- **Two different `lib/` directories.** The repo-root `lib/` (33 MB of vendored jars) and the
  Ant/Ivy build were deleted in `75461de` — Maven resolves all dependencies now. But
  `src/net/lump/lib/` is **live source**. Don't conflate them.
- **Never `setLayout()` on a generated form panel to add a row.** The `.form`-generated
  panels use IntelliJ's `GridLayoutManager`, and a populated one cannot be given another row:
  replacing its layout discards the constraints every existing child was added with, so they
  all collapse to `0x0` and vanish. Adding the time-zone picker that way emptied the entire
  Server tab. Wrap instead — lift the generated panel out of its cell into a plain
  `BorderLayout` wrapper and hang the new row beneath it (`Preferences.addTimeZoneRow`). Check
  with a `pack()`-and-measure probe: a control the layout dropped has zero bounds.
- **The client is distributed from the server's front page.** `mvn package` builds the fat
  jar in `prepare-package` and the war plugin copies it to `WEB-INF/client/envelope-client.jar`
  — under `WEB-INF` so Tomcat never lists it, but deliberately **not** under `WEB-INF/lib`,
  which is the webapp classpath (a 31 MB fat jar there would load a second Hibernate into
  the server). `FileServer.feedClient` hands it out as `/envelope-client-<version>.jar`, the
  version being the server's own `Revision.version()` — the war and the jar inside it are
  built from the same version files, so nothing in the build names the file; the bare
  `/envelope-client.jar` is still served, and a stale versioned name redirects to the
  current one. The landing page shows the version and gives run instructions naming the
  host the visitor reached. All the Java Web Start
  machinery — `Jnlp.java`, `jnlp.xml`, `feedJnlp`, the pack200 branches, `/lib/*` and `/us/*`
  serving of `WEB-INF/lib`, `/info/security.policy`, `/log4j.properties` — was removed in one
  go; Web Start left with JDK 11. The client only ever calls `/invoke`.
- **`@Version` needs sub-second stamp columns.** Every versioned entity maps `@Version` onto a
  `stamp` column. Declared as plain `timestamp` (whole seconds) the version Hibernate hands
  back to the client can never match the value the row holds, so the *first* save of an entity
  succeeds and the second is refused with `StaleObjectStateException` — every entity editable
  exactly once per load. `sql/migrations/001-version-stamp-precision.sql` moved the live tables
  to `timestamp(3)`; keep any new versioned table at `timestamp(3)` too. **And declare it**:
  every stamp is `@Column(…, secondPrecision = 3)`. Hibernate 7 generates a version timestamp
  at the column's declared precision, or the dialect's default when none is declared — 6 for
  MariaDB — so undeclared, a freshly inserted row came back carrying microseconds the column
  had already dropped, and its next save was refused as stale (5.6 ticked in milliseconds,
  which hid this). `precision` does nothing for a timestamp; `secondPrecision` is the
  Jakarta Persistence 3.2 attribute Hibernate reads.
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
  `Query.myBudget` hands it to `State.budget`, which the
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
- **`Transaction.equals` compares the transaction's own columns only, and must not be
  "improved" into comparing allocations.** It used to build both sides of an allocation
  comparison from `this.allocations` (the second copy unqualified), so it compared the list to
  itself and always passed — useless, and worse: the sort comparator dereferenced
  `one.getId()`, so as soon as the form's graph held **two** allocations with a null id among
  them it threw a `NullPointerException` out of `equals`. `TimSort` never calls a comparator
  on a list shorter than two, which is why a fresh single-allocation transaction was safe and
  this went unnoticed. The null id is not transient: `TransactionForm` appends an empty row on
  Tab or Down and `sendAllocationChange` refuses to save one with no category or amount, so it
  lives as long as the form. The only caller, `TransactionChangeHandler.sendChanges`, runs on
  the EDT outside any try, so every description, entity and date edit was silently dropped
  from then on. The comparison is now gone. Do not reinstate it against `that.allocations`
  either: `Allocation.equals` compares its `transaction`, so the two would recurse into each
  other and overflow the stack for any two transactions whose scalar fields match. Allocation
  edits never travelled through here — they are saved on their own by `sendAllocationChange`.
- **`sendError` does not return.** It only marks the response and suspends Tomcat's output
  buffer, so execution carries on: `InvocationServlet` had three `sendError` calls with no
  `return` after them, and a request refused for a bad `Accept`, an unsupported encoding or
  an unrecognized part name still dispatched and **committed** while the client was told 415
  or 406. Adding the returns exposed a second bug behind them — `Accept-Encoding: identity`
  (and `*`) had never been handled, and only ever worked because the 406 didn't stop the
  response going out. Both are fixed; if you add another `sendError` on that path, return.
- **`/configure` fails closed.** `ServerPrefs.configure` used to skip its whole Basic-auth
  block when `configure.username`/`configure.password` were empty, which is what
  `ServerPrefs.properties` ships — so a bare war in a plain Tomcat served the config form,
  and the live database password inside it, to anyone who could reach the webapp. It now
  answers 503 until both are set, so any new deployment must supply
  `SERVERPREFS_CONFIGURE_USERNAME` and `SERVERPREFS_CONFIGURE_PASSWORD` (both compose stacks
  already do). The form also no longer renders stored secrets at all: `type="password"` only
  masked them in a browser while leaving the value in the page source. An empty password
  field on submit means "leave it alone".
- **The test suite no longer touches the real client settings, and must not be allowed to
  again.** `TestSuite`'s static initializer writes a test host, port and username, and
  those used to land in the very `java.util.prefs` store the Swing client reads
  (`~/.java/.userPrefs/net/lump/...`) — so `-DskipTests=false` silently repointed the
  client at `<hostname>:8080` as user `bowmantest` and left it there, after which the
  client and every probe failed with `ConnectException` and a settings dialog.
  `ServerSettings` and `LoginSettings` now resolve their node through
  `client/ui/prefs/PrefsNode`, which honours `-Denvelope.prefs.node=<path>`; `pom.xml`
  points surefire at `net/lump/envelope/test`, and `TestSuite` **refuses to run** if that
  property is unset rather than risk the real node. Saving and restoring the node in a JVM
  shutdown hook was tried first and does not work — ordering against the preferences
  system's own flush hook is unspecified, and losing that race left the node cleared.
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
- **Tests are skipped by default** (`default-skip-tests` profile); run with
  `-DskipTests=false`. **The suite passes** — 38 tests — but only those that need no
  server pass on their own; point the rest at a running stack:

  ```
  mvn test -DskipTests=false \
    -Denvelope.test.host=localhost:7041 -Denvelope.test.user=guest -Denvelope.test.password=guest
  ```

  Without that it targets `<hostname>:8080` as `bowmantest`, which exists nowhere, and the
  server-dependent tests fail fast naming the host and user they tried. Four things had to
  change to get here, all worth not undoing: `TestSuite`'s static initializer called
  `System.exit(1)` on a failed handshake, which killed the surefire fork outright so not one
  test — including the server-independent ones — ever reported; `Portal` answers a failed
  connection with a modal `JOptionPane` and a visible Preferences window
  (`Portal.java:144-183`), which nothing dismisses in a test JVM, so surefire now runs
  headless and those become a `HeadlessException` the tests record; `TestBalance` is excluded
  in `pom.xml` because it is an abandoned OFX experiment with every line commented out, and
  matching surefire's `Test*.java` default it failed the run on "No tests found"; and
  `TestRevision` asserted a non-empty value for `Revision.Locker`, which CVS leaves empty
  exactly like `Revision.Name` that it already skipped.
- **`TestDates` is where date behaviour is pinned.** Twenty tests across eight zones either
  side of UTC, covering the wire form, `java.sql.Date`, the commands that carry dates
  through `WireFilter` (and the filter refusing anything else), the table column and both
  date editors. It needs no server, and is deliberately **not** in
  `TestSuite.suite()` — that class's static initializer demands a handshake and nothing in
  `TestDates` does. It pins the *stock* `JTextFieldDateEditor`'s staleness on purpose, so if
  a future jcalendar fixes it that test fails and `ZonedDateEditor` can go. Its comparisons
  go through `assertSameDay`, not `assertEquals`: JUnit reports a `java.sql.Date` through
  `toString()`, which renders in the default zone, so two instants a day apart print
  identically and a real failure reads `expected:<2026-08-30> but was:<2026-08-30>`.
- **A database failure used to surface as a bare `NullPointerException`.** `DAO.initialize`
  swallowed `buildSessionFactory()`'s exception, leaving the factory null; every request then
  died in `getCurrentSession()` with nothing in the message, while the real reason — on the
  swarm, `Access denied for user 'budget'` — sat one line in the startup log as an unhelpful
  `ServiceException`. It now keeps the root cause: the log says
  `could not build the session factory -- <cause> (url …)` and requests fail with `the
  database is not available: <cause>`. `/info/ping` is **liveness only** — it never touches
  Hibernate, so a container sat "healthy" for hours with no database. `/info/ready` dispatches
  `Command.Name.ready` — a session-less command beside `ping` on the `Security` facet — through
  `Controller` exactly as a client's command goes, and
  answers `503 not ready: <cause>` when that throws; the image, dev compose and swarm health
  checks all use it. It used to construct a `Generic` DAO in `FileServer` and finish the
  transaction by hand, and leaked a session per probe until that was debugged: **nothing
  outside `Controller` should construct a DAO.** `Controller` writes a command's answer as a
  serialized object, so the probe reads it back the way a client would, and the `Single-Object`
  / `Object-Count` headers ride along on the probe's response. `InvocationServlet` is
  `load-on-startup`, so a bad database shows at startup rather than at the first user's login;
  the probe re-runs `DAO.initialize` each time, so a server that started without its database
  recovers when the database appears. **What `ready()` asks is the latest transaction's day,
  twice** — through the entity mapping (the `java.sql.Date` read every client query makes) and
  as the text the database spells the column in — and it refuses readiness if they disagree
  (`not ready: the latest transaction is stored on 2026-09-14 but read back as 2026-09-13`).
  That proves the schema (the swarm bootstraps *empty* databases, so "wrong database" is a
  real way to be up with every login failing) and stands as a permanent test of the date
  handling in the `Transaction.date` trap above: a server given a zone east of Greenwich goes
  unhealthy saying which day became which. It used to count `users`, which on that cadence
  read in the log like someone probing accounts and told any unauthenticated caller how many
  there were; a healthy body is `ready: latest transaction 2026-09-14`, or `ready: no
  transactions yet` on a fresh schema. `max(date)` is answered from the `date` index
  (migration 005) without touching the table.
- **`transactions` carried only its primary key until migration 005.** The list is filtered
  on date in both shapes, but only the category list was index-driven — by
  `allocations.category`, one of the two indexes InnoDB made for that table's foreign keys;
  `allocations` has no date column. The account list started from `transactions` with nothing
  to narrow it and scanned the whole table (`EXPLAIN: type index`), filtering the period out of
  that. `sql/migrations/005-transactions-date-index.sql` adds `index date (date)`, which the
  account list and the readiness probe's `max(date)` both use.
- **`mvn package` rewrites the bind-mounted war under a running Tomcat**, which triggers a
  reload; requests landing mid-reload fail with `IllegalStateException: this web application
  instance has been stopped already` while the health check still answers `pong` (it never
  touches the database). Always `docker compose restart tomcat` after packaging, even for a
  client-only change — the war is rebuilt either way. But `restart` never re-reads
  `compose.yml`: a changed health check, or any other container config, needs `up -d`,
  which recreates the container. The dev container ran for nine days on the old
  `/info/ping` probe after the file had said `/info/ready`.
- **Every log line says which request it was logged for, and a transaction every 10–15 s
  is the health check.** `RequestContextFilter` puts `METHOD /uri remote-address` in the
  log4j MDC for the whole of every request, `Controller.invoke` adds `user commandName`
  once it has authenticated one, and both layouts in `log4j.properties` print them (`%X`
  works there because the log4j 1 bridge builds its `PatternLayout` on Log4j2's). So DAO's
  `began transaction` reads `[GET /envelope/info/ready 127.0.0.1] [no-session ready] began
  transaction` for Docker's `HEALTHCHECK` — every 10 s in dev compose, 15 s in the image, from inside the
  container hence `127.0.0.1` — and `[POST /envelope/invoke 10.0.0.5] [guest
  listTransactions] began transaction` for a client. Nothing else probes `/info/ready`:
  HAProxy's `check` on the backend is TCP-only. Keep such per-thread context going through
  `ThreadInfo`, which the filter clears in a `finally` — Tomcat reuses worker threads, and a
  key left set would label the next request's lines with this one.
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
- **Permissions say what a user may do; the budget checks say to which rows.** Both apply to
  every data command: `Controller.dispatch` checks the `Permission` bit, and the DAO method
  checks every id it was given with `DAO.requireMyAccount` / `…Category` / `…Transaction` /
  `…Allocation` / `…PresetRow` / `requireMyBudget`. Those checks ask the database a scalar
  question (the id of the budget a row belongs to) rather than loading the row, because a save
  that loaded the row first would collide with the client's copy of it
  (`NonUniqueObjectException`) — that collision is also, by accident, why guest could not
  rewrite its *own* `User` row before 0.12, only insert new ones. A transaction belongs to a
  budget only through its allocations, so `requireMyTransaction` insists **every** allocation
  is the caller's, and refuses a transaction with none. A missing row and someone else's row
  get the same refusal ("there is no account 7 in your budget"). Admins get no exemption: ADMIN
  is about users and budgets, and the user/budget commands remain global.
- **Known, unfixed, low-severity** (from the 0.12 review): `getChallenge` and
  `authChallengeResponse` answer an unknown user with `Invalid_User` but a wrong password with
  `Invalid_Credentials`, so user names can be enumerated without logging in, and
  `getChallenge` hands any user's salt to anyone; `ready` tells anyone the latest transaction
  date across every budget; `setPassword` does not ask for the current password.
- **Sessions are per-JVM and in memory** (`Sessions`), so they die on redeploy and would need
  sticky sessions or a shared store if a second Tomcat ever appeared.
