# Transaction Ledger API

A backend API for a simple transaction ledger. You can create accounts, post credit and debit transactions, read balances and paginated transaction history, and transfer money between accounts atomically.

| Submission item | Where to find it |
|---|---|
| Source code | `src/main` (application) and `src/test` (45 automated tests) |
| README | This file |
| API documentation | [docs/API.md](docs/API.md), plus the live Swagger UI at <http://localhost:8080/swagger-ui.html> while the app is running |
| Assumptions | [ASSUMPTIONS.md](ASSUMPTIONS.md) |

## Tech stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 8+ |
| Framework | Spring Boot 2.7 (Spring Web, Spring Data JPA, Bean Validation) |
| Database | H2 (embedded, file-based), schema managed by Flyway |
| API docs | springdoc-openapi (Swagger UI) |
| Tests | JUnit 5, Spring Boot Test, MockMvc |
| Build | Maven (wrapper included, so no separate Maven install is needed) |

Spring Boot 2.7 was chosen so the project runs on Java 8 as well as newer JDKs (11, 17, 21).

## Running locally

Prerequisite: a JDK, version 8 or newer, on your `PATH` (`java -version`). The Maven wrapper downloads Maven itself on first run.

```bash
# macOS / Linux
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

The API starts on <http://localhost:8080>. To use another port, set the `PORT` environment variable, for example `PORT=8085 ./mvnw spring-boot:run` (PowerShell: `$env:PORT="8085"; .\mvnw.cmd spring-boot:run`).

Alternatively, build a jar and run it:

```bash
./mvnw package
java -jar target/transaction-ledger-api-1.0.0.jar
```

Data is stored in `./data/ledger.mv.db` and persists across restarts. To start with an empty ledger, stop the app and delete the `data/` folder.
The H2 web console is at <http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:file:./data/ledger`, user `sa`, empty password).

### Running the tests

```bash
./mvnw test
```

The suite has 45 tests and uses an in-memory database. It covers every requirement in the assignment, including:

- concurrent debits against one account, which never overdraw it
- concurrent duplicate submissions, which create exactly one transaction
- opposing concurrent transfers, which neither deadlock nor lose money
- direct SQL checks that the database constraints reject invalid data

## Quick tour (curl)

```bash
# Create two accounts
A=$(curl -s -X POST localhost:8080/api/v1/accounts -H 'Content-Type: application/json' -d '{"name":"Alice"}' | sed -E 's/.*"id":"([^"]+)".*/\1/')
B=$(curl -s -X POST localhost:8080/api/v1/accounts -H 'Content-Type: application/json' -d '{"name":"Bob"}'   | sed -E 's/.*"id":"([^"]+)".*/\1/')

# Credit 100, debit 30
curl -s -X POST localhost:8080/api/v1/accounts/$A/transactions -H 'Content-Type: application/json' \
     -d '{"type":"credit","amount":100,"description":"Salary","reference_id":"r-1"}'
curl -s -X POST localhost:8080/api/v1/accounts/$A/transactions -H 'Content-Type: application/json' \
     -d '{"type":"debit","amount":30,"reference_id":"r-2"}'

# Re-sending r-1 returns 409 DUPLICATE_TRANSACTION; the balance stays 70
curl -s -X POST localhost:8080/api/v1/accounts/$A/transactions -H 'Content-Type: application/json' \
     -d '{"type":"credit","amount":100,"reference_id":"r-1"}'

# Balance and history
curl -s localhost:8080/api/v1/accounts/$A/balance
curl -s "localhost:8080/api/v1/accounts/$A/transactions?type=credit&page=1&limit=10"

# Transfer 30 from A to B
curl -s -X POST localhost:8080/api/v1/transfers -H 'Content-Type: application/json' \
     -d "{\"from_account_id\":\"$A\",\"to_account_id\":\"$B\",\"amount\":30,\"reference_id\":\"t-1\"}"
```

PowerShell equivalent of the first steps:

```powershell
$A = (Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/accounts -ContentType application/json -Body '{"name":"Alice"}').id
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/accounts/$A/transactions" -ContentType application/json `
  -Body '{"type":"credit","amount":100,"reference_id":"r-1"}'
Invoke-RestMethod "http://localhost:8080/api/v1/accounts/$A/balance"
```

## Project structure

```
src/main/java/com/example/ledger
├── LedgerApplication.java          Entry point; provides the Clock bean
├── account/                        Account entity, repository, service, controller, DTOs
├── transaction/                    Ledger entry entity (immutable), posting logic, history, controller, DTOs
├── transfer/                       Account-to-account transfer service, controller, DTOs
├── common/                         Pagination envelope, error codes, exception -> HTTP mapping
└── config/                         OpenAPI metadata
src/main/resources
├── application.properties
└── db/migration/V1__create_ledger_schema.sql   Tables, constraints, indexes
src/test/java/com/example/ledger    API, concurrency and schema-constraint tests
docs/API.md                         Endpoint reference
ASSUMPTIONS.md                      Interpretation decisions
```

## How data integrity is guaranteed

- **Atomicity.** Each request that changes money runs in a single database transaction: one posting, or both legs of a transfer. Inserting the transaction and updating the balance commit together or not at all. Every business error (insufficient balance, duplicate, missing account) is thrown before commit and rolls back everything, so a transfer can never be applied halfway.
- **Concurrency.** Before posting, the account row is locked with `SELECT ... FOR UPDATE` (a pessimistic write lock). Concurrent requests for the same account therefore run one at a time, and the duplicate check, the balance check and the balance update can't interleave. Transfers lock both accounts in a fixed order (by account ID), so two opposite transfers can't deadlock.
- **Database constraints** back up the application checks:
  - `UNIQUE (account_id, reference_id)`
  - `CHECK (balance >= 0)`
  - `CHECK (amount > 0)`
  - `CHECK (type IN ('CREDIT','DEBIT'))`
  - non-blank `name` and `reference_id`
  - a foreign key from `transactions.account_id` to `accounts.id`
  - `NOT NULL` on all required columns

  If a constraint is ever hit, the API returns the matching error (`409` or `422`), not a `500`.
- **Immutability.** There is no API to update or delete a transaction. The transaction entity is annotated as `@Immutable` with non-updatable columns, and its repository exposes only `save` and read methods. An account's `name`, `id` and `created_at` are also non-updatable; only `balance` changes, and only through postings.
- **Balance consistency.** The stored balance changes only in the same database transaction that inserts the ledger entry, so it always equals the sum of all accepted credits minus all accepted debits. Rejected requests leave no trace.
- **Money handling.** Amounts are `BigDecimal` values stored as `DECIMAL(19,2)`. Floating-point types are never used.

## Assumptions

See [ASSUMPTIONS.md](ASSUMPTIONS.md) for the decisions made where the assignment left room for interpretation.
