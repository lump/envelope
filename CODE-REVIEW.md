# Code review — Envelope (`src/`), verified

A review of all 94 Java files under `src/` (18 677 lines) was produced by Deepseek; this
file is that review after every claim was re-checked against the tree at `3cf9301`. The
original text is preserved in git history of this file if it is ever wanted; what follows
replaces it, because a claim and its verdict are only useful together.

**Outcome: nothing was fabricated and no cited line number was wrong.** Every code-level
fact held up. What did not hold up sits one layer above the code — eight findings misdescribe
or overstate what their defect actually causes, two of the ten "must-fix" items are
mis-ranked, one worked example is arithmetically impossible, and **the finding ranked #1 is
not a bug at all**: the grouping it objected to is what keeps an auto-deduct pair visible in
a category's history, and collapsing it would have silently blanked 4219 rows of this budget.
Those are recorded inline as **Correction:** and **REJECTED**.

The lesson the rejection carries: every code-level claim in this review is trustworthy, and
every claim about what the code *should* do needs checking against the data and the intent
before it is acted on.

Re-verification also turned up five defects the review missed, two of them worse than
anything on its own must-fix list: a reflected XSS in `/env` on an endpoint it recorded as
clean, and an abandoned open transaction that the next request on the same Tomcat worker
inherits. Those are marked **not in the original**.

§1 was reproduced against the running dev stack, including timing measurements; §4's
`Money` and `EmacsKeyBindings` claims were reproduced by compiling and running against
`target/classes`. The rest is source-level verification.

Verdicts: **CONFIRMED** = code matches and the failure path is real. **PARTLY** = the code
fact is right, the consequence or severity is not. Line numbers are against `3cf9301`.

| Section | Claims | Confirmed | Partly |
|---------|--------|-----------|--------|
| 1 Server | 8 + 2 positives | 6 + 1 | 2 + 1 |
| 2 Client core | 10 + 1 positive | 7 + 1 | 2, 1 rejected |
| 3 Shared protocol & entities | 6 | 6 | 0 |
| 4 `lib/` utilities | 8 | 7 | 1 |
| 5 UI — transaction editing | 6 | 5 | 1 |
| 6 UI — shell, tree, forms, prefs | 9 | 9 | 0 |

Items already documented in `CLAUDE.md`'s Traps section are not re-reported except where a
*new* failure mode was found (which is the case for `Transaction.equals`, #4).

---

## Status

Everything below is verified. What has been **fixed and re-verified against the running
stack**: the `/env` XSS; all three missing `sendError` returns (4 accepted requests → 4
dispatches, 4 refused → 0); the abandoned open transaction; the null-`command` NPE; the
deflate double-wrap; `/configure` failing open, rendering secrets, and 500-ing on a
colonless credential; `readUpTo`'s quadratic scan (1/2/4 MiB now 17/9/13 ms, was
308/1175/4653) and the charset mangling with it (a part with no `Content-Length` used to be
a `StreamCorruptedException`, now round-trips); the `Controller` and `/info/ready` session
churn (closing/disconnect log lines per request: 0, transactions balanced); `Transaction
.equals`; the Balance/Reconciled seeds; the `LoginSettings` credential key; the
`TableQueryBar` cast; the off-EDT tree mutations; the in-flight-insert ghost allocation; the
`TransactionTableModel` reload tear and its dead wait loop; and all three `Money` defects.
The category-list **grouping** was rejected (see §2.1); its order-by was fixed.

**The test suite passes — 17 tests, 0 failures**, where it previously could not report at
all. See *What running the suite turned up*.

Still open, all recorded below and none of them firing today: the `importNew` debounce race,
`Portal`'s modal dialogs on a library path, `State.entities()` blocking the EDT, the
uncoalesced tree rebuild, `StatusBar.changeTask`, `LimitedStack`, `User.getPermissions` on a
NULL column, `Account.rate`/`ceiling` on `double` columns, `Account.categories` EAGER, the
2007-grade wire crypto, `Crypt`'s `java.util.Random` salts, `MonitorInputStream`'s off-by-one,
`Spinner`, `Fonts`, `EmacsKeyBindings`, `ObjectUtil.deepCopy`, `CipherOutputStream.flush`,
`ChildFirstClassLoader`, `AllocationSetting`, and the unscoped-budget reads already in
`CLAUDE.md`.

## Fix in this order

Re-ranked by whether the defect fires today, which is not the order the original used.

**Firing now, user-visible, money or data wrong on screen**

1. ~~`CriteriaFactory.java:260` — category transaction list groups by `Allocation.id`.~~
   **Rejected as a bug; the grouping is deliberate.** See §2.1 — the real defect in that
   query is its order-by, now fixed.
2. `TransactionTableModel.java:224-225` — Balance and Reconciled column seeds are crossed.
   (§2.9)
3. `Transaction.java:204` — `equals` NPEs once the form holds two allocations and one has a
   null id, and every transaction-scalar save is then dropped. (§3.1)
4. `LoginSettings.java:98-102` — saved password is keyed by host with no username, so it is
   replayed for the next user and locks them out. (§6.1)
5. `TableQueryBar.java:100` — unguarded model cast throws on every key release until the
   first tree selection. (§6.2)
6. `Hierarchy.java:484,503` — tree model and selection mutated off the EDT after every
   allocation save. (§2.6)
7. `TransactionTableModel.java:183` — the "wait until filled" loop is a permanent no-op, and
   the vector is never cleared on reload. (§2.10, §2.11)

**Server — found only by re-verification, not in the original review**

8. `FileServer.java:179,185,156-157` — `/env` reflects unescaped request data into
   `text/html`, unauthenticated. Reflected XSS, shipping live. The original recorded
   `FileServer` as a clean positive. (§1.10)
9. `InvocationServlet.java:158,317` — a junk `key` part abandons a `Security` DAO with an
   **open transaction holding row locks**, and `DAO.java:45` makes the next request on that
   Tomcat worker inherit it. (§1.3)

**Deployment exposure**

10. `InvocationServlet.java:230,235,274` — missing `return` after `sendError`: the request
    executes and commits while the client is told it was refused. Three sites, not the two
    reported. (§1.1)
11. `ServerPrefs.java:137` — `/configure` has no authentication when its credentials are
    empty, which is what `ServerPrefs.properties` ships; `:141-143` 500s on a colonless
    credential. Both shipped stacks do set the credentials. (§1.9)
12. `InvocationServlet.java:241` — NPE on a multipart with no `command` part. (§1.3)

**Real but latent — no live path reaches them**

`Money.equals`/`hashCode` (§4.1), `Money(double)` (§4.2), `StatusBar.changeTask` (§2.3),
`LimitedStack` (§5.3), `User.getPermissions` on a NULL column (§3.2), `AllocationSetting`
(§3.3, dead), `ChildFirstClassLoader` (§4.8, dead), `CipherOutputStream.flush` (§4.6).

**Posture, not a bug** — `/configure` rendering, wire crypto, `Crypt` salts, the unscoped
`DetachedCriteria` reads already in `CLAUDE.md`.

---

## 1. Server

Every bullet here was reproduced against the running dev stack, not only read.

### `InvocationServlet.java`

- **CONFIRMED (reproduced) — missing `return` after the 415 on a bad `Accept` header**
  (`:235`). `sendError` only suspends the Tomcat output buffer, so the subsequent
  `getOutputStream()`/`addHeader`/writes silently no-op while dispatch proceeds to
  `Controller.invoke()` at `:279`, whose `finally` commits unconditionally
  (`Controller.java:209-212`). Reproduced with a hand-rolled multipart `ping` and
  `Accept: text/html`: client got **415**, server logged `Received command ping` → `began
  transaction` → `committed transaction`. The `:274` twin (`SC_NOT_ACCEPTABLE` on
  encodings) reproduced identically as **406** with the same commit.
  **There is a third instance the original missed:** `:230`,
  `else rp.sendError(SC_NOT_ACCEPTABLE, "argument … not recognized")`, also has no `return`,
  so an unrecognized part name falls through to the same place.
  The signature is verified *and spent* before any of this (`Security.java:154`, called from
  `:197-198`), so a retry needs a fresh signature — and then double-applies the write.
  Reachability: the stock client always sends the right `Accept` (`HttpClient.java:69`), so
  this needs a non-stock client or a header-stripping proxy.
  Fix: `return` after each of the three.

- **CONFIRMED (reproduced) — missing `break` in the `deflate` negotiation branch**
  (`:268-272`; gzip breaks at `:266`, deflate does not).
  **Correction, and the mechanism is the opposite of both first readings:** the original said
  the header "names only the outer gzip". It does not — `addHeader` appends, so the response
  carries `Content-Encoding: deflate, gzip`. And the object nesting
  (`GZIPOutputStream` wrapping `DeflaterOutputStream`) inverts on the wire: bytes written to
  the gzip stream are gzipped, then deflated by the outer wrapper, so **deflate is the
  outermost layer on the wire**. Reproduced with `Accept-Encoding: deflate, gzip`: body began
  `78 da` (raw zlib) and a header-following client died with
  `java.util.zip.ZipException: Not in GZIP format`. Control run with `gzip` alone began
  `1f 8b` and decoded fine. The defect stands; neither description of the failure did.
  Fix: `break` after the deflate wrap.

- **CONFIRMED (reproduced) — NPE at `:241` (`command.getSeqId()`) when the multipart carries
  no `command` part.** `command` is declared null at `:94`. Reproduced two ways, both
  **500**: a multipart with no parts, and a part with an unrecognized name — the latter only
  because `:230` also lacks a `return` (above).
  **The session leak is broader than claimed.** A `key` part with junk bytes logs `began
  transaction` (from `new Security()` at `:158` → the `DAO` instance initializer at
  `DAO.java:44-46`) with no matching commit or close, and exits via the *caught*
  `InvalidKeyException` at `:317` — not only via the NPE. Because `DAO.java:45` skips
  `begin()` when a transaction is already active, the next request on that Tomcat worker
  **inherits an abandoned open transaction, still holding its row locks.** This, not the
  `close()`/`disconnect()` churn below, is the real cross-request hazard.
  Fix: track whether the command part was seen, `sendError(400)`, and close the `Security`
  DAO on every exit path.

- **CONFIRMED (reproduced) — no-Content-Length part bodies round-trip binary through the
  platform charset** (`:141` → `readUpTo(…).toString().getBytes()`, with
  `s.append(new String(buffer, 0, read))` at `:348`). Reproduced with the same 319-byte
  serialized `ping`: with the part's `Content-Length` → **200**; without it → **500** and
  `java.io.StreamCorruptedException: invalid stream header: EFBFBDEF` — the `ac ed` magic
  decoded to U+FFFD and re-encoded as `ef bf bd`.
  **Correction: "the stock client always sends `Content-Length`" is false.**
  `HttpClient.java:160` sends it for the `command` part only; the `key` part
  (`:118-126`) has none, so **every encrypted request takes this path today.** It survives
  only because base64 is ASCII.
  **A further bug the original missed:** `readUpTo` returns a *char* offset that `:360`
  feeds to `bis.skip(end)` as a *byte* count, so any multi-byte content also desynchronizes
  the stream.
  Fix: boundary-scan raw bytes, never through `String`.

- **CONFIRMED (measured) — `readUpTo` re-runs the boundary regex over the whole accumulated
  buffer every 1 KB** (`:348-352`). One unauthenticated `ping` each, part sent without
  `Content-Length`:

  | part size | via `readUpTo` | with `Content-Length` |
  |-----------|----------------|-----------------------|
  | 1 MiB     | 308 ms         | 12 ms                 |
  | 2 MiB     | 1 175 ms       | 16 ms                 |
  | 4 MiB     | 4 653 ms       | 17 ms                 |

  3.8-4.0× per doubling — quadratic, and `READLIMIT` = 104857600 (`:36`) is the only cap.
  Unauthenticated remote CPU exhaustion in a single request.
  Fix: keep the last match offset and scan only the new window; add a realistic per-part cap.

- **CONFIRMED — minor waste.** `:181-189` regrows and copies the whole accumulator per 4 KB
  chunk. `:107` and `:116` compile a `Pattern` inside the per-part loop, and `:83-87`
  compile five more per request.

### `Controller.java`

- **PARTLY — `close()` before `disconnect()` churns sessions; the headline is right, the
  cause and the fix are not** (`:222-223`). Measured with Hibernate statistics against the
  dev database, replicating `:209-224`:

  ```
  after new Generic()   opened=1 closed=0
  after a query         opened=1 closed=0
  after commit()        opened=1 closed=1   <- already closed AND unbound
  after close()         opened=2 closed=2   <- opened a NEW session just to close it
  after disconnect()    opened=3 closed=2   <- opened a THIRD, left bound to the thread
  ```

  So "~2 sessions per request" and "a brand-new session that stays until the reused worker's
  next request" are both right. **Correction:** the ThreadLocal was already empty *before*
  `close()` — with `hibernate.current_session_context_class=thread`
  (`DAO.properties:50`), `ThreadLocalSessionContext` has auto-close enabled and its
  `CleanupSync` unbinds at transaction completion, so the **commit** disposed of the request's
  session and `close()` opened a throwaway of its own. The proposed fix (`disconnect()`
  first) therefore only halves the churn; the correct fix is to **drop both calls**.
  "Latent cross-request state hazard" is also overstated here: the inherited session is
  freshly opened with nothing loaded or pending. The genuine hazard is the abandoned *active
  transaction* from the servlet's exception paths, above.

- **CONFIRMED (positive)** — the permission gate at `:104-113` runs before the method is
  resolved at `:120`, and cannot be bypassed: `Command.java:139-145` forces
  `requiredPermission = 0L` for the no-session constructor, `:154-160` forces
  `sessionRequired = true` for the permission-taking one, and `Controller.java:64-66` rejects
  a session-required command with a null user. `Action.deleteAllocation`'s locking guard
  (`Action.java:67-85`) and `deleteTransaction`'s children-then-parent delete inside one
  commit (`:147-161`) are sound as documented.

### `ServerPrefs.java` / `FileServer.java`

- **PARTLY — the `/configure` gate is exactly as described; the claimed exposure is not.**
  Confirmed: `:137`'s condition wraps the *entire* Basic-auth block (`:138-155`), so an empty
  username or password skips it; `ServerPrefs.properties` ships both empty; a GET renders
  every key with its live value (`:215`, `:228`) and an unauthenticated POST writes any
  `<Class>.<key>` parameter plus the `.ok` flags (`:169`, `:173`); and `web.xml` maps
  `DefaultServlet` to `/` with no `security-constraint`, with the landing page linking
  `/configure` (`FileServer.java:117`).

  **Correction: the severity rested on "the documented swarm path" being open, and it is
  not.** `docker/compose.yml:39-40` and `docker/swarm-stack.yml:42-43` both set
  `SERVERPREFS_CONFIGURE_USERNAME`/`_PASSWORD`, and `:64-67` turns those into prefs.
  Verified live: `GET /envelope/configure` → **401** with
  `www-authenticate: Basic realm="ServerPrefs"`. What is actually left:

  - The hole opens only when those variables are unset — the bare war in a plain Tomcat. And
    then it is loud rather than silent: an empty `configure.password` also forces
    `configured = false` (`:74-75`), so `FileServer.java:48` routes **every** path to the
    form.
  - An unauthenticated POST does **not** rewrite the live connection: `DAO.initialize` is a
    no-op once built (`DAO.java:61`), so a rewrite takes effect only on restart.
  - The rendering is worse than stated. `:234` appends `type="password"` *after* the value at
    `:228`, so the masking is cosmetic. Verified live on the authenticated form:
    `name="DAO.hibernate.connection.password" value="tegdub" type="password"`, alongside the
    configure password itself.
  - **Not in the original:** `:141-143` splits the decoded credential on `:` and indexes
    `creds[1]` unguarded, so a correct username with an absent password
    (`Basic base64("admin")`, or `-u admin:`) is an unauthenticated **500** (AIOOBE) instead
    of a 401. Reproduced. The comparison is also not constant-time, and
    `docker/compose.yml:40` defaults to `admin`/`envelope`.

  Fix: fail closed — require configured credentials before any read or write — redact
  password-class values from the GET rendering, and bound-check the credential split.

- **PARTLY — "`FileServer` serves only fixed constants; no traversal."** The traversal half
  is right: the only resource read is `getResourceAsStream(CLIENT_JAR)` with `CLIENT_JAR` a
  `private static final` (`:33,125`), and every other route is a fixed `matches()` on
  `getServletPath()` (`:58-78`) with nothing user-controlled reaching a path.
  **But it does not serve only constants, and this is a new finding:** `env()` (`:150-197`)
  reflects request data into `text/html` unescaped and unauthenticated — parameter names at
  `:185`, header values at `:179`, path info at `:156-157`. Verified live:
  `GET /envelope/env?%3Cscript%3Ealert(1)%3C/script%3E` returns a literal
  `<script>alert(1)</script>` with `Content-Type: text/html`. **Reflected XSS on a debug
  endpoint that ships live in the webapp.** Fix: escape the output, or drop `/env`.

---

## 2. Client core

### `CriteriaFactory.java`

- **REJECTED on the grouping, CONFIRMED on the order-by** (`:259-260,268-269`). The code
  facts below are all correct; the conclusion drawn from them is not.

  **The per-allocation grouping is deliberate and stays.** Measured against the live
  budget: 4440 transaction+category pairs render as more than one row, and **4219 of them
  net to zero** — they are auto-deduct pairs. Grouping by `t.id` collapses each to a single
  `0.00` row, which is what `CLAUDE.md` means by the pair "keeping the payment in the
  category's history": the two rows are the +140 that arrived and the −140 that left, not a
  duplicate. They differ in sign, carry the same date and description, and the reconcile
  checkbox is read-only in this view (`TransactionTableModel:347-348`), so the repeated
  `t.id` is never ambiguous in practice. Verified on category 34: 424 transactions → 848
  rows before, 424 netted rows after, with transaction 1529's `+140.00`/`−140.00` becoming
  one `0.00`. The change was made, reviewed against the data, and reverted.

  **The order-by was genuinely wrong, and is fixed.** An unqualified `Order.asc("stamp")`
  on an `Allocation` root is the *allocation's* version stamp, so rows were sequenced by
  when each allocation was last saved and two transactions sharing a date had their rows
  interleaved. Demonstrated on category 53, 2010-09-30 — transaction 4966's two amounts sat
  either side of 4968's:

  ```
  before: 4971 +600.00 | 4969 -33.39 | 4966 -751.80 | 4968 -807.59 | 4966 -500.89 | 4968 -44.00
  after:  4971 +600.00 | 4969 -33.39 | 4968 -807.59 | 4968  -44.00 | 4966 -751.80 | 4966 -500.89
  ```

  Now `t.date, t.stamp, stamp`: the transaction's stamp keeps its rows together, the
  allocation's orders them within it. `t.stamp` is functionally dependent on the `a.id`
  group key through the join, so this is safe even if `ONLY_FULL_GROUP_BY` is ever turned on
  (the live `sql_mode` does not set it).

  The original's supporting detail, for the record: Root is `Allocation` (`:261`) and the only alias is `transaction → t`
  (`:262`), so the unqualified `groupProperty("id")` and `Order.asc("stamp")` both resolve to
  the root: one row per *allocation*, ordered by the allocation's stamp. The account branch
  groups `Transaction.id` (`:240-241`) and gets one row per transaction.

  The scenario is real: `Action.java:261-263` passes the same `preset.getCategory()` for
  both halves of an auto-deduct pair, so every Pay Day transaction holds two allocations in
  one category and lists twice in the category view, once in the account view.

  **Corrections:** (a) the dropped 7th column is the `groupProperty("id")` *allocation* id,
  not `t.id` — `t.id` is index 5 and **is** consumed as `COLUMN.TransactionID`. The
  allocation id is what tells two rows of one transaction apart, which is the grouping
  working as intended rather than evidence against it. (b) "a sign the grouping was never
  intended" is backwards, and `TransactionTableModel.java:53-63` declaring a
  never-populated `COLUMN.AllocationID` points the same way: someone built this view around
  per-allocation rows. (c) "accumulates wrong running balances" is wrong, not merely
  overstated: `updateTableFor` accumulates per row from an `Allocation`-rooted seed, so the
  running total is internally consistent and ends at the correct category balance. The
  genuinely wrong seed is §2.9.

### `MonitorInputStream.java`, `ThreadPool.java`, `StatusBar.java`

- **PARTLY — EOF is counted as a byte read** (`client/MonitorInputStream.java:42,48,54`).
  The code fact holds on all three overloads: `fireRead(++bytesRead)` runs on the terminal
  `-1`, and `bytesRead += nr` adds `-1`.
  **Correction:** "or negative" is unreachable — `fireRead` resets only in non-cumulative
  mode (`:71`) and the sole construction is `cumulative = true` (`HttpClient.java:195`), so
  the value can only be off by one. The direction differs per overload, which the original
  flattens: `read()` over-counts, the array overloads under-count. The effect is a one-byte
  discrepancy in a formatted byte display. Cosmetic, not bug severity.

- **CONFIRMED — `StatusBar.changeTask` remove-by-value compares against a null local**
  (`:102-106`). `e` is null entering the loop, `task.getValue().equals(e)` is always false,
  and the parameter `o` is never compared, so `:108` short-circuits and the spinner spins
  forever. `ThreadPool.afterExecute:55` passes `t` — the `Throwable`, null on success —
  where `beforeExecute:44` registered `new StatusElement(t)` with the `Thread`, and the
  static type selects the broken `removeTask(Object)` overload.
  Latent verified: all 13 `execute(...)` call sites pass a `StatusRunnable`, and there is no
  `submit(...)` anywhere, which is the one inherited path that would wrap a task in a
  non-`StatusRunnable` `FutureTask`. Fix: match on `o`, register and remove the same element.

- **CONFIRMED — `ThreadPool` is `(0, MAX_VALUE)` over a `SynchronousQueue`** (`:31-33`).
  **Correction:** "the singleton double-checked-init is racy" understates it — `:30` is a
  single unsynchronized check-then-act with no `volatile` and no second guarded check, so
  two threads can each build a pool and one is silently discarded. Same shape at
  `StatusBar.java:26` and `State.java:55`.

### `State.java`, `Hierarchy.java`, `ClientTrust.java`

- **CONFIRMED — `State.entities()` blocks on an HTTP round trip on the caller's thread**
  (`:103-113`), and the caller is the EDT: `TransactionChangeHandler:222` calls
  `form.refreshEntities()` (`TransactionForm.java:460-463`) from inside the
  `SwingUtilities.invokeAndWait` block opened at `TransactionChangeHandler:141`, so the
  query runs on the EDT *and* blocks the submitting pool thread.
  **Correction:** the cache is `timeToLive 20 s, timeToIdle 10 s` (`State.java:42-44,49-50`),
  so the miss window is 10 s idle, not ~20 s — more frequent than stated.
  Fix: fill the entity cache on the thread pool.

- **CONFIRMED — tree model and selection mutated off the EDT in `refreshTree`**
  (`:484,503`). Both statements are bare on the pool worker (`StatusRunnable` declared at
  `:409`, dispatched at `:519`) while every neighbouring notification correctly uses
  `invokeLater` (`:443-445,463-470,476-478,507-514`). Runs after every allocation save
  (`TransactionChangeHandler:476,542,601` → `refreshTotals:618-624`).
  Two refinements: the selection-listener cascade fires only in the position-shift case,
  since `setSelectionPaths` no-ops on an equal path; and the original under-reports —
  `node.remove` (`:442`), `dmtn.setUserObject` (`:461`), `removeAllChildren` (`:459`) and
  `node.add` (`:474`) also run off-EDT, so the structure mutates *before* the deferred
  notification. Fix: wrap both, and prefer doing the whole node surgery on the EDT.

- **PARTLY — `cachedwidth` map is write-only** (`:827-834`, declared `:758`).
  **Correction:** it *is* read, at `:827` (`containsKey`) and `:830` (`get`). The accurate
  statement is that the reads only decide whether to overwrite an entry with itself, and
  `:834` assigns unconditionally, so no read influences output. Dead branch, safe to delete;
  the wording was wrong, the conclusion stands. No boxing-identity bug hides at `:830`.

- **CONFIRMED (positive)** — `ClientTrust.EitherTrust` catches both `CertificateException`
  and `RuntimeException` (`:144-152`), degrades correctly in both directions (`:59,64-72`),
  and unions both stores in `getAcceptedIssuers` (`:173-178`). Matches the documented design.

### `TransactionTableModel.java`

- **CONFIRMED — Balance/Reconciled seeds are swapped** (`:224-225`). `reconciled` is seeded
  from `beginningBalance` (unfiltered, loaded `:127-130`) and `balance` from
  `beginningReconciledBalance` (reconciled-only, `:135-138`) — precisely crossed. Crossed at
  row 0 only; later rows chain consistently from the wrong seed, so Balance is understated
  and Reconciled overstated by `beginningBalance − beginningReconciledBalance` for the whole
  filtered list.
  Refinement on the "jump": `setValueAt:303` uses the correct seed only for `row == 0`, so
  the visible jump needs a *first-row* toggle, and only in the account view — the checkbox
  is read-only in the category view (`:347-348`). Fix: swap the two initializers.

- **CONFIRMED — the "wait until filled" loop is a permanent no-op** (`:183`).
  `(System.currentTimeMillis() - 20000) > startTime` is false on first evaluation because
  `startTime` was taken at `:179`.
  **Correction to the proposed fix:** flipping to `<` is not a safe one-character change. On
  an empty result the listener never fires, `finished[0]` stays FALSE, and `filled` (`:47`,
  `:218`) is never reset per task — so it keeps the previous task's index, the predicate
  stays true, nothing calls `notifyAll`, and the queue thread stalls the full 20 s on every
  empty category or date range. Resetting `filled` per task is part of the fix.

- **CONFIRMED — the `transactions` vector is never cleared when a reload task starts**
  (`:108-111,177-207`). Rows are overwritten in place (`:247`) and the only trim is
  post-fill (`:194-198`), which `catch (AbortException ignore)` at `:202` skips entirely.
  The new title is already on screen from the EDT selection listener
  (`Hierarchy.java:142`), so a failed query leaves a torn mix of two categories under the
  new heading. Fix: clear on task start, on the EDT, before the first row lands.

---

## 3. Shared protocol & entities

All six confirmed; line citations exact.

- **CONFIRMED — `Transaction.equals` NPEs on a null-id allocation** (`:198-204`). `:199`'s
  `allocations` is unqualified, so both lists are copies of `this.allocations` — the
  documented self-comparison bug — but that does not neutralise the NPE, because
  `Collections.sort` is called on each list separately (`:209,211`) and the comparator
  dereferences `one.getId()` at `:204` within one list.

  **Correction to the must-fix wording:** "*Any* unsaved (null-id) allocation makes
  `equals` throw" is overbroad. TimSort returns before touching the comparator when
  `hi - lo < 2`, so a 0- or 1-element list never throws; the threshold is **two
  allocations, at least one with a null id**. A fresh single-allocation transaction is safe.

  Everything else is worse than stated. `sendChanges()` (`TransactionChangeHandler:399-431`)
  has no try/catch — the only one is *inside* the `StatusRunnable` at `:408-427`, after the
  throw — and it runs on the EDT via a `javax.swing.Timer` (`Changeable.java:39-47`), so the
  NPE reaches the EDT's uncaught handler: a stack trace on stderr, `editing` already
  mutated, `pristine` never advanced, and the form showing an edit the database never got.
  And the null id is not transient: `TransactionForm.java:724-727` appends an allocation with
  null id, category and amount on Tab or Down, and `sendAllocationChange:446` refuses to save
  a row with no category or amount — so it survives for the life of the form.

  Fix: **drop the allocation comparison from `equals` entirely.** It is a documented no-op,
  and `CLAUDE.md` is explicit that repointing `:199` at `that.allocations` makes
  `Transaction.equals` and `Allocation.equals` mutually recursive — a `StackOverflowError`
  for any two transactions whose scalar fields match. Track allocation edits explicitly.

- **CONFIRMED — `User.getPermissions()/setPermissions(Long)` NPE on a NULL column**
  (`:119-130`). `permissions.toLong()` at `:121` NPEs on a null field, and
  `new Permission(permissions)` at `:129` unboxes a null `Long` into a primitive `long`, so
  hydration itself fails the moment Hibernate sets a NULL column. The column is nullable:
  `bootstrap-mysql.sql:245` is `permissions int(11) default NULL`. `Controller.java:107`
  calls `getPermission()` with no null guard either. Fix: treat null as 0 and add a
  `NOT NULL DEFAULT` migration.

- **CONFIRMED — `AllocationSetting` is dead and broken.** Commented out of both the mapping
  (`DAO.java:70-71`) and the schema (`bootstrap-mysql.sql:38-50`), with no migration
  re-adding it. `toString` (`:32-37`) formats `{0}:{2}@{3}` over args `name, type, budget`:
  `{1}` (the type) is never referenced and is dropped, `{2}` picks up the budget, and `{3}`
  has no argument so the literal `{3}` leaks into the output. `getBudget(Budget)` (`:63-67`)
  takes a parameter and so is not a JavaBeans getter — Hibernate would not map the
  association if it were re-enabled.

- **CONFIRMED — salts from `java.util.Random`** (`:910-917`), a clock-seeded 48-bit LCG, in
  an md5-crypt scheme with 1000 rounds (`:624`). Below the bar the rest of the auth design
  sets. Use a shared `SecureRandom`.

- **CONFIRMED — `Account.rate`/`ceiling` declared NUMERIC over `double` columns.** `rate` is
  a bare `BigDecimal` (`:113-114`) and `ceiling` uses `MoneyType`, whose `sqlTypes()` is
  `{Types.NUMERIC}` (`MoneyType.java:32`), but `bootstrap-mysql.sql:29-30` declares both
  `double`. Migration 004 fixed `allocation_presets.allocation` the same way and left these
  two behind. Only zeros are written today. Migrate to `decimal(24,14)`.

- **CONFIRMED — `Account.categories` is EAGER and nothing reads it** (`:103-107`). The only
  callers, `:136` and `:146`, sit inside the block comment at `:132-152`; the tree reads its
  category lists from `State`'s own map, filled by `CriteriaFactory.getCategoriesForAccount`.
  One wasted SELECT per account per tree refresh. Make it LAZY.

---

## 4. `lib/` utilities

- **PARTLY — `Money` equals/hashCode contract violation** (`:191-202`). **Fixed.** `equals` is
  `compareTo`-based and scale-insensitive; `hashCode` is `value.unscaledValue().hashCode()`
  and scale-sensitive. Real and demonstrated:

  ```
  Money("1.21") vs Money(BigDecimal "1.2100")      equals=true   hash 121 vs 12100
  Money("1.10").multiply(Money("1.10")) = 1.2100   equals Money("1.21"), hash 22000 vs 220
  ```

  **Correction 1 — the worked example is impossible.** `1.21` and `1.2100` are the *same*
  double literal, so `new Money(1.21)` and `new Money(1.2100)` are bit-identical and hash
  identically. The violation needs a genuine scale difference, which only the `BigDecimal`,
  `double`, `int`/`long`/`BigInteger` and `multiply`/`divide` routes produce — `Money(String)`
  is the one constructor that normalises (`:49`).

  **Correction 2 — it is latent, so #7 on a "highest impact first" list is too high.** Every
  live path lands on scale 2: `allocations.amount` is `decimal(8,2)`, and `Action.valueOf`
  explicitly rescales in both branches (`:175`, `:179`). `Money.multiply(Money)` and
  `divide(Money)` are defined but called from nowhere in `src`. The only scale-0
  constructions are `new Money(0)`, and zero's unscaled value is 0 at every scale, so it
  cannot collide. `Allocation.hashCode:162` does fold `amount.hashCode()` in, so the mine is
  armed — it is simply not being stepped on. Fix:
  `value.stripTrailingZeros().hashCode()`. `MoneyType.hashCode:47-48` already does exactly
  this, which is the inconsistency in miniature.

- **CONFIRMED — `Money(double)` stores the raw binary expansion** (`:57-59`). **Fixed.**
  `new BigDecimal(val)`, never `BigDecimal.valueOf`, and never rescaled. Reproduced:

  ```
  new Money(19.99).toBigDecimal()   = 19.989999999999998436805981327779591083526611328125
  new Money("19.99").toBigDecimal() = 19.99
  equals = false
  ```

  Both render `$19.99` from `toString()`, so the corruption would be invisible on screen —
  worse than stated. The double overload is currently called from nowhere at all, not merely
  "with integral values". Fix: `BigDecimal.valueOf(val)`, or rescale as the String
  constructor does.

- **CONFIRMED — `divide(Money)` returns ZERO on a zero divisor** (`:135-138`) while the
  scaled overloads throw. Reproduced: `divide(zero)` → `$0.00` silently;
  `divide(zero,2,HALF_UP)` → `ArithmeticException: / by zero`; `divide(three)` →
  `ArithmeticException: Non-terminating decimal expansion`. The API offers a silent wrong
  answer or an unresolvable crash for the same operation. Force callers onto the
  scale-taking overloads and let `BigDecimal` throw.

- **CONFIRMED — Emacs killring records the paste plus trailing text**
  (`EmacsKeyBindings.java:314`). `jtc.getText(start, end)` is `(offset, length)`, so it reads
  `end` characters from `start`. Reproduced against a real `JTextField`: a paste of `"XY"`
  at offset 2 records `"XYCD"`, and a paste in the second half of the field throws
  `BadLocationException`, which `:315-317` prints and swallows — the paste is never
  recorded and Alt-Y yanks a stale entry. Fix: `getText(start, end - start)`.

- **CONFIRMED — wire crypto is 2007-grade** (`Encryption.java:25-38`): RSA-1024,
  SHA1withRSA, `DESede/ECB/PKCS5Padding` — no IV, and ECB leaks block structure across
  streamed objects. `InvocationServlet.java:245-249` passes the client's `Accept-Encryption`
  header into `Cipher.getInstance` with only whitespace trimming and no whitelist.
  **`CLAUDE.md` calls this body "AES-encrypted", which is wrong** — the original's inference
  that the doc is why it was never re-audited looks right. Fix the doc regardless of whether
  the crypto is redone (RSA-2048+, SHA256withRSA, AES-GCM with a per-request IV,
  server-side algorithm whitelist).

- **CONFIRMED — `CipherOutputStream.flush()` finalizes the cipher and the next `write()`
  re-keys** (`net/lump/lib/util/CipherOutputStream.java:99-110,146-157`). `flush()` does
  `out.write(cipher.doFinal())` then `valid = false`; `write` re-runs `initCipher()`, a fresh
  `Cipher.getInstance` + `init`. A write-flush-write emits an undecodable stream, silently.
  Dormant because the sole caller writes once and closes (`HttpClient.java:154-156`). The
  asymmetry is real and sharper than stated: `InvocationServlet` imports `javax.crypto.*`
  with no explicit import of the custom class, so its `new CipherOutputStream(os, cout)` at
  `:251` resolves to the **JCE** class, which does not finalize on flush. Different
  implementations on each side of the wire.

- **CONFIRMED — `ObjectUtil.deepCopy` swallows failure and returns null** (`:26-43`), and
  both live callers dereference it: `TransactionChangeHandler:95-96` NPEs immediately on the
  EDT, and a null `pristine` from `:422` turns `:406`'s guard into "always unequal" — a
  spurious full-transaction save on every keystroke. `Transaction.equals` is null-safe at
  `:168`, so it degrades to false rather than throwing. Fix: let it throw, or null-check.

- **CONFIRMED — `ChildFirstClassLoader` is dead and defective.** `getResource` can return
  null, so `resource.openConnection()` NPEs (`:50-53`) outside the `catch (IOException)` at
  `:80` and escapes as a raw NPE instead of `ClassNotFoundException`; the blind
  `(HttpURLConnection)` cast breaks any `jar:`/`file:` resource; and `:62-72` reallocates and
  copies the whole accumulated buffer per 128-byte read. No instantiation anywhere in `src`.

---

## 5. Client UI — transaction editing

- **CONFIRMED — unsaved allocation kills scalar saves.** See §3.1 for the mechanism, the
  two-allocation threshold, and why the null id persists.

- **CONFIRMED — deleting a row whose insert is still in flight leaves a ghost allocation**
  (`TransactionChangeHandler:565-599`). The id is snapshotted under `synchronized
  (savesInFlight)` at `:568-571`, but the save path publishes the id *outside* that monitor
  (`:472-473`) and only touches `savesInFlight` to register before the round trip
  (`:451-457`) and remove in the `finally` (`:481-483`). So the monitor buys visibility of an
  already-landed save and never serialization with one in flight — the comment at `:565` is
  wrong on its own terms. `if (id != null)` is false, the server delete is skipped, the
  insert commits a row no view holds, and every balance is a `sum(amount)` over allocations.
  `deletesInFlight` guards only delete-versus-delete.
  Refinement: the ghost needs a *slow but successful* insert. On an `AbortException` the id
  stays null and skipping the delete is correct.
  Fix: honour a pending-delete flag in the save's `finally`, or re-read the id after it lands.

- **CONFIRMED — `LimitedStack`** (`:9-25`). The `(int)` constructor discards `size` and never
  assigns `stackLimit` (`:12-14`), and `push` trims with `>` *before* pushing (`:20-24`), so
  the steady state is limit+1 — default 11, and a `LimitedStack(3)` would hold 4. Inert
  because `changeHistory` is write-only: the only references are the declaration (`:37`),
  `clear()` (`:86`), and two `push` calls (`:87`, `:411`) — no pop, peek, get or iteration
  anywhere. Two facts to add: the `(int)` constructor is never called, and `extends Stack` is
  raw so `<E>` is decorative. Fix: assign `stackLimit`, trim after the push with `>=`.

- **PARTLY — `importNew` swaps the editing graph without flushing pending debounced edits**
  (`:78-97,140-160`). The mechanism is confirmed: `importNew` never stops a timer,
  `setFormData` detaches only *listeners* (`:148-152`) while building new `Changeable`
  instances, and each stale instance still owns a live `dirtyTimer`. The delays are right
  (500 ms for text fields, 5 ms for the amount field at `:168`). "No warning" is
  understated — `updateAction`'s else branch *clears* the "Save Pending" label
  (`Changeable.java:58`), which looks exactly like a completed save.

  **Correction — the two consequences are swapped between fields.** The amount field cannot
  move money to the wrong transaction this way: the stale `Changeable` wraps the same shared
  component, whose text `setFormData` resets to B's value before any timer can fire, so
  `saveState` compares equal and returns false; and in the one window where the field still
  holds A's text while `editing` is B, `:88` has just set `isExpense = null`, so `:172` NPEs
  on unboxing instead of writing. The *description* field does the opposite of "dropped" in
  that same window: `getValue()` (A's text) ≠ `getState()` (B's description), so `:212`
  writes **A's text onto transaction B** and `sendChanges()` persists it. Outside that
  window the silent drop is exactly as described.
  Fix: capture the transaction identity at submit time and refuse to persist on mismatch;
  cancel pending timers on switch.

- **CONFIRMED — amount-field sign applied twice under the Expense view** (`:170-177`).
  `-` is admitted by the field's key filter (`MoneyTextField.java:44`) and parsed by
  `Money(String)`. In Expense view the field shows the value unsigned (`:133-135`), so a
  typed `-50` gives `getValue() = -50`, `:173` negates it to `+50`, and
  `writeAmountThroughToSingleAllocation` (`:329-338`) stores an income row under an Expense
  radio. The UI masks it: `:394` redisplays the balance negated, and `syncAmountToBalance`
  returns early while the field has focus (`:348`), so both on-screen numbers still read as
  an expense. Fix: accept only unsigned input in that view.

- **CONFIRMED with corrections — `MoneyRenderer` allocates per cell per repaint.** The class
  declares no fields, `label` is a local rebuilt every call (`:26`) with a fresh
  `CompoundBorder`/`EmptyBorder`/`Insets` (`:28`), discarding the reusable `JLabel` inherited
  from `DefaultTableCellRenderer`. The renderer does call `table.editCellAt` from
  `getTableCellRendererComponent` (`:56-59`).
  **Corrections:** the cited range `:18-67` is the method minus its closing brace (18-68,
  class closes 69). And "works only because nothing else paints these tables" is wrong — the
  guard is `isCellEditable` at `:57`. `MoneyRenderer` *is* installed on the main transaction
  table (`Hierarchy.java:141`), but there only column 0 is editable
  (`TransactionTableModel:347-349`) and column 0 is the checkbox, never a Money column, so
  the side effect never fires there. It fires only on the allocations table, where both
  columns are editable.

---

## 6. Client UI — shell, tree, forms, prefs

All nine confirmed; line citations exact.

- **CONFIRMED — saved login credential is keyed by host only** (`:98-102,136-144`):
  `ENCRYPTED_PASSWORD + "." + getHostName()`, no username. Replay traced:
  `Preferences.java:421` commits whatever username is typed, `:423`'s `setPassword` is a
  no-op while the field shows the `PASSWORD_ALREADY_SET` placeholder (`LoginSettings:109`),
  so `password` stays null and `:191`'s `password == null && shouldPasswordBeSaved() &&
  passwordIsSaved()` hands back A's blob under B's username. The server hashes it against
  B's salt → `Invalid_Credentials`, and B's real password is discarded until "remember
  password" is unchecked. Fix: key on username too, and skip the saved fast path when the
  dialog's username differs.

- **CONFIRMED — Space-to-reconcile casts the model unconditionally**
  (`TableQueryBar.java:100`). The cast runs on **every** key release, before the
  `VK_SPACE` check. `table` is a bare `new JTable()` (`:385`) and the only `setModel` is
  `Hierarchy.java:139`, behind a tree-selection callback — so any keystroke before the first
  selection is a `ClassCastException`. The sibling delete path guards properly at `:282`.
  Fix: `instanceof`.

- **CONFIRMED — Apple menu integration is dead on every modern JDK**
  (`MainFrame.java:126-130`). `mrj.version` is a legacy Apple-JDK property, unset on
  OpenJDK 9+ including on macOS, and `AppleStuff` (the only place `java.awt.Desktop`
  About/Preferences/Quit handlers are wired) is constructed nowhere else. `mac` is therefore
  always false and `:140-147` adds the duplicate generic menu entries on every platform.
  Fix: gate on `Desktop.isDesktopSupported()`, or drop the check.

- **CONFIRMED — preset editor reload races in-flight saves**
  (`AllocationPresetEditor.java:145-163,310-343`). `reload()` replaces `State.getPresets()`
  wholesale with freshly-queried instances (`:150-155`) while `save()` mutates the *old*
  object after its round trip (`:328-329`), so a completed save is invisible to the rebuilt
  view and retyping the same value early-returns at `:445`. The stale-stamp refusal is also
  sound: `savesInFlight` is keyed by object identity (`:311-317`), so the re-fetched row is a
  different key and is not deduped. Correctly labelled likely-bug. Fix: drain
  `savesInFlight` before reload.

- **CONFIRMED — `ServerSettings.setContext` stores the context unnormalized**
  (`:116-118`), and `getCodeBase` (`:190-192`) concatenates it straight after the port.
  Reproduced: `new URL("http://host:7041envelope/")` →
  `MalformedURLException: Error at index 4 in: "7041envelope"`. `Preferences.java:457` passes
  the text field through unchecked, and `HttpClient.java:60` is the only path to `/invoke`,
  so every request fails with nothing pointing at the field. Only the URL branch of
  `setHostName` normalizes, and only trailing slashes. Fix: normalize here too.

- **CONFIRMED — `infoQuery()` has no timeouts and runs on the EDT** (`:120-136`): no
  `setConnectTimeout`/`setReadTimeout` anywhere, and every caller is a direct
  `ActionListener`/`MouseListener` with no `SwingWorker` or pool hop
  (`Preferences.java:337-378`), so an unreachable host freezes the dialog for the platform
  TCP timeout.
  **Correction:** the cache (`:231-246`) is not per-host — it is a single unkeyed static
  flag, so switching to a brand-new host inside the 3 s window still returns a cached "ok".
  Worse than described, not overstated. Fix: 3-5 s timeouts, run off-EDT, key or drop the
  cache.

- **CONFIRMED — `Spinner` re-arms a new one-shot `Timer(33)` forever** (`:58-79`). The `if`
  at `:61` gates only the repaint; the `Timer` construction and `start()` at `:63-70` are
  unconditional and outside it, so `redo()` re-arms for the `Spinner`'s whole lifetime
  regardless of `twirling` — ~30 timers and EDT wake-ups a second while idle. The first
  `paintImmediately` also runs off-EDT, from the plain `animator` thread (`:26,58,73-78`).
  Fix: one repeating timer, started and stopped from `setSpinning()`.

- **CONFIRMED — every allocation save triggers an uncoalesced full-tree rebuild**
  (`TransactionChangeHandler:476,618-628` → `Hierarchy.refreshTree`). Round trips counted:
  `getAccountTotals` and `getCategoriesForAccount` each issue two sequential queries
  (`CriteriaFactory:77-107,132-162`), and the recursive walk calls the latter once per
  account — **2 + 2A**, all sequential inside one `StatusRunnable`, dispatched onto the
  unbounded pool (`Hierarchy:518`) with nothing coalescing repeats. Fix: debounce, or update
  only the sums.

- **CONFIRMED — `Fonts` fallback chain is dead** (`:30-40`). `new Font(name, style, size)`
  never returns null — an unresolvable family silently maps to a substitute — so `f != null`
  is true on the first iteration and the loop always takes `fonts[0]`. `Lucida Grande`,
  absent on Linux and Windows, is therefore always chosen and renders as Dialog; the rest of
  every fallback list is unreachable. Fix: probe `getAvailableFontFamilyNames()`.

---

## What running the suite turned up

`CLAUDE.md` recorded the suite as not passing — hanging on Swing dialogs, with
`TestMoney.testPrint` failing outright. It passes now, 17 tests, and getting there found
things the review did not:

- **`TestSuite`'s static initializer called `System.exit(1)`** on a failed handshake, which
  killed the surefire fork: `The forked VM terminated without properly saying goodbye`, and
  not one test reported — including the six that need no server at all. That is why
  `testPrint`'s failure sat unexamined; nobody could see a result.
- **`Portal` answers a failed connection with a modal `JOptionPane` and a visible
  Preferences window** (`Portal.java:144-183`). Nothing dismisses those in a test JVM, which
  is the documented "hangs on Swing dialogs". Surefire runs headless now, so they raise
  `HeadlessException` instead, and the server-dependent tests fail fast naming the host and
  user they tried.
- **The prefs clobbering is fixed at the source, not worked around.** `ServerSettings` and
  `LoginSettings` resolve their node through the new `client/ui/prefs/PrefsNode`, which
  honours `-Denvelope.prefs.node`; surefire points it at a throwaway node and `TestSuite`
  refuses to run without it. A save-and-restore shutdown hook was tried first and is a trap
  in its own right: ordering against the preferences system's own flush hook is unspecified,
  and losing that race left the node **cleared** rather than restored.
- **`TestMoney.testPrint` was two bugs, not one.** The documented half is real — the
  constructor rounded `HALF_UP` while the Javadoc and `toString()` promise `HALF_EVEN`, so
  `"1.025"` became 1.03 before `toString()` could round it to `$1.02`. Fixing that advanced
  the test to input 2 and exposed a second: **`Money` could no longer parse accounting
  negatives at all.** In 2009 the en_US currency pattern carried a `(¤#,##0.00)` negative
  subpattern, so `"($1.025)"` parsed and `toString()` emitted it; CLDR dropped it, the
  pattern is now `¤#,##0.00`, and parenthesised input fell through both formatters into a
  `NumberFormatException` from the last-ditch `BigDecimal` parse. Anyone typing `($5.00)`
  into the amount field hit it. The constructor handles the parentheses itself now, and the
  test's expected strings — which still read `($1.02)` — were updated to the `-$1.02` a
  modern JDK renders.
- **`TestEncryption.testSymmetric` could never have passed.** It read the bytes *left over*
  after `ObjectInputStream.readObject()` had already consumed the whole object and asserted
  that count equalled the serialized size: 724 against the `-1` that `read()` answers at
  EOF. It asserts the round trip now.
- **`TestRevision.testExistence` skipped `Revision.Name` but not `Revision.Locker`**, and CVS
  leaves both empty unless it has a reason to fill them, so it died on a
  `NullPointerException`. (Worth knowing separately: `Revision`'s values are frozen CVS
  keywords from 2009 — `Revision.nameOrState()` feeds `AboutBox` the string `Exp`.)
- **`TestBalance` is an abandoned 2008 OFX experiment** with every line commented out,
  importing a `net.ofx.types` that is not in the project. It matches surefire's
  `Test*.java` default, so it was picked up, found no runnable method, and failed the whole
  run on a "No tests found" warning. Excluded in `pom.xml`, kept for the intent it records.

## What held up well

Re-checked and sound: signature verification before `readObject`
(`InvocationServlet:195-208`); single-use stamps inside a 5-minute window
(`Security:117-155`); the locking `deleteAllocation` guard; `setExpense` committing open
editors before the view flips; `syncAmountToBalance` listener detachment; the `Day`
midnight-UTC discipline; `EitherTrust` CA bundling; `FileServer` constant-only serving;
`ZonedDateEditor` re-set-from-source; the tree `updateChildren` position-based rebuild
post-fix; the permission gate at dispatch (which cannot be bypassed — the constructors force
it); and `Action`'s one-transaction create/delete pairs.

`FileServer` was on the original's list and comes off it: no path traversal, but `/env`
reflects unescaped request data (§1.10).

## `CLAUDE.md` corrections this review turned up

- The request body is **not** AES. It is `DESede/ECB/PKCS5Padding` under an RSA-1024-wrapped
  session key, signed SHA1withRSA (`Encryption.java:25-38`).
- Worth adding to Traps: `Transaction.equals`'s allocation sort NPEs at two allocations with
  a null id, which is the concrete cost of the self-comparison quirk already documented
  there.
