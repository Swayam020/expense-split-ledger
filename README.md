# Expense-Split Ledger

A group expense tracker built on a double-entry ledger. Balances are never stored — they are
derived from immutable ledger entries, so they reconcile by construction instead of being
recalculated and hoped over.

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-336791)

> **Status: early development.** M0 is complete — the application boots and connects to a real
> PostgreSQL instance. The domain model, ledger and REST API are in progress. See [Roadmap](#roadmap).

---

## Why a ledger instead of a balance column

The obvious design gives every group member a `balance` column and adds to it on each expense.
It works until it doesn't: a failed transaction leaves the column wrong with no record of what
it should have been, a retried request double-counts, and there is no way to answer "how did
this number get here?" The balance is an assertion nobody can verify.

Double-entry bookkeeping inverts that. Every expense writes a set of ledger entries that sum to
zero. Balances are a `SUM` over those entries, computed on read. A balance cannot drift from its
history, because it *is* its history — and the invariant `SUM(debits) = SUM(credits)` can be
asserted for any group at any point in time.

### A worked example

Alice pays ₹100 for a dinner shared equally by Alice, Bob and Carol. That writes four entries:

| Member | Entry  | Amount  | Why                                   |
|--------|--------|---------|---------------------------------------|
| Alice  | credit | ₹100.00 | She paid the bill; the group owes her |
| Alice  | debit  | ₹33.34  | Her own share                         |
| Bob    | debit  | ₹33.33  | His share                             |
| Carol  | debit  | ₹33.33  | Her share                             |

Credits total ₹100.00, debits total ₹100.00. The entries sum to zero, so the group's books
balance. Net position per member is a sum over their own rows:

```
Alice   +66.66      Bob   −33.33      Carol   −33.33      Total  0.00
```

₹100 divided three ways is ₹33.33 × 3 = ₹99.99. The remaining paisa is not dropped and not
allowed to float — it is assigned to a specific member by a deterministic, documented rule, so
the same expense always produces the same rows.

---

## What this project demonstrates

- **Double-entry bookkeeping** — every transaction writes balanced debit and credit rows, with
  the zero-sum invariant enforced and tested rather than assumed.
- **Idempotent settlements** — a settle-up carries an `Idempotency-Key`. A retry caused by a
  dropped response replays the original result instead of paying twice, including when the
  retry arrives while the first request is still in flight.
- **Correct money arithmetic** — `BigDecimal` throughout, with scale and `RoundingMode` fixed at
  defined boundaries and the remainder unit explicitly assigned rather than lost to rounding.
- **Settlement minimisation** — reducing N members with N mutual debts to a smaller set of M
  transfers, with the algorithm and its optimality bounds documented.
- **Property-based testing** — the ledger invariant is asserted across generated transaction
  sequences, not just hand-picked cases.

---

## Tech stack

| Layer     | Choice                                              |
|-----------|-----------------------------------------------------|
| Language  | Java 17                                             |
| Framework | Spring Boot 4.1.1 (Web MVC, Data JPA, Validation, Actuator) |
| Database  | PostgreSQL 17                                       |
| ORM       | Spring Data JPA / Hibernate                         |
| Testing   | JUnit 5, Testcontainers                             |
| Build     | Maven (via wrapper)                                 |

---

## Getting started

### Prerequisites

- JDK 17 or newer
- A container runtime — Docker or Podman
- No local Maven installation is required; the repository ships the Maven Wrapper

### 1. Start PostgreSQL

```bash
docker run -d --name ledger-pg -p 5432:5432 \
  -e POSTGRES_PASSWORD=ledger -e POSTGRES_DB=ledger \
  postgres:17
```

Podman is a drop-in substitute — replace `docker` with `podman` and use the fully qualified
image name `docker.io/library/postgres:17`.

### 2. Run the application

```bash
./mvnw spring-boot:run
```

The application starts on port 8080 and creates its schema on first run.

### 3. Verify

```bash
curl -s localhost:8080/actuator/health | python3 -m json.tool
```

A healthy instance reports the database component explicitly:

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": { "database": "PostgreSQL", "validationQuery": "isValid()" }
    }
  }
}
```

A bare `{"status":"UP"}` is not sufficient — an application with no database at all returns the
same thing. The `db` component is what distinguishes *started* from *connected*.

---

## Configuration

| Property                         | Default                                  | Notes                                     |
|----------------------------------|------------------------------------------|-------------------------------------------|
| `spring.datasource.url`          | `jdbc:postgresql://localhost:5432/ledger` | JDBC connection string                    |
| `spring.datasource.username`     | `postgres`                               |                                           |
| `spring.datasource.password`     | `${DB_PASSWORD:ledger}`                  | Reads `DB_PASSWORD`; falls back for local dev |
| `spring.jpa.hibernate.ddl-auto`  | `update`                                 | Development convenience; not for production |
| `spring.jpa.open-in-view`        | `false`                                  | Disabled so lazy-loading faults surface in tests |

Override any of these with an environment variable rather than editing the committed file, for
example `DB_PASSWORD=… ./mvnw spring-boot:run`.

---

## Testing

```bash
./mvnw test
```

Integration tests run against a real PostgreSQL instance started by Testcontainers, not an
in-memory substitute — the ledger's constraints and `NUMERIC` semantics are database behaviour,
and H2 does not reproduce them faithfully.

<details>
<summary><b>Running the tests under Podman</b></summary>

Testcontainers is a Docker API client and does not detect Podman on its own. Expose Podman's
Docker-compatible socket and point Testcontainers at it:

```bash
systemctl --user enable --now podman.socket
export DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true
```

`TESTCONTAINERS_RYUK_DISABLED` is required because Ryuk, the resource-reaper sidecar, needs
privileges that rootless Podman does not grant. With it disabled, containers from an
interrupted test run may need clearing manually via `podman ps -a`.

</details>

---

## Roadmap

**Stage one — core ledger**

- [x] **M0** — Project scaffold, PostgreSQL connection, `/actuator/health` reporting `db: UP`
- [ ] **M1** — Domain model: `Group`, `Member`, `Expense`, `ExpenseShare` with JPA mappings
- [ ] **M2** — `LedgerEntry` and expense posting, with debits equal to credits after every post
- [ ] **M3** — REST API: create group, add expense, read derived balances; DTOs and centralised error handling

**Stage two — correctness and scale**

- [ ] **M4** — Settlements with `Idempotency-Key`, stored request hash and replayed responses
- [ ] **M5** — `BigDecimal` audit: scale, rounding mode and deterministic remainder assignment
- [ ] **M6** — Settlement minimisation, N members reduced to M transfers
- [ ] **M7** — Testcontainers integration suite and a generated-sequence invariant test in CI
- [ ] **M8** — Schema diagram, `compose.yaml`, and deployment

---

## Project structure

```
src/main/java/com/swayam/ledger/
├── LedgerApplication.java
└── …                           domain, ledger, web and service packages land here

src/test/java/com/swayam/ledger/
├── LedgerApplicationTests.java
├── TestcontainersConfiguration.java   PostgreSQL container wired via @ServiceConnection
└── TestLedgerApplication.java         local entry point backed by Testcontainers
```

`TestLedgerApplication` runs the application against a throwaway PostgreSQL container instead of
a local one, which is useful when the development database is not running.
