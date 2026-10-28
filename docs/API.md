# Transaction Ledger API — Reference

Base URL: `http://localhost:8080/api/v1`

- All request and response bodies are JSON (`Content-Type: application/json`).
- Field names use `snake_case`.
- Money values are decimal numbers with at most 2 decimal places (for example `150.00`).
- Timestamps are ISO-8601 in UTC (for example `2026-10-27T18:06:51.917Z`).
- Interactive docs: Swagger UI at `/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs`.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/accounts` | Create an account |
| `GET` | `/accounts/{account_id}` | Get an account |
| `GET` | `/accounts/{account_id}/balance` | Get an account's current balance |
| `POST` | `/accounts/{account_id}/transactions` | Add a credit or debit transaction |
| `GET` | `/accounts/{account_id}/transactions` | Transaction history (filter, paginate, latest first) |
| `POST` | `/transfers` | Transfer money between two accounts (bonus) |

There are no `PUT`, `PATCH` or `DELETE` endpoints for transactions. Those requests return `405 Method Not Allowed`.

---

## 1. Create account

`POST /accounts`

Request:

```json
{ "name": "Alice" }
```

| Field | Type | Rules |
|---|---|---|
| `name` | string | Required, not blank, at most 255 characters. Leading and trailing spaces are trimmed. |

The client may send only `name`. Any other field (`balance`, `id`, `created_at`, ...) is rejected with `400 INVALID_REQUEST_BODY`.
The server generates `id` (UUID) and `created_at`, and the balance always starts at `0.00`.

Response `201 Created` (with a `Location` header pointing at the new account):

```json
{
  "id": "1a93bb57-1035-4e88-bae2-0d7ffc0aa8ef",
  "name": "Alice",
  "balance": 0.00,
  "created_at": "2026-10-27T18:06:51.100Z"
}
```

Errors: `400 VALIDATION_ERROR`, `400 INVALID_REQUEST_BODY`.

## 2. Get account

`GET /accounts/{account_id}` returns `200` with the same shape as above. If the account doesn't exist, it returns `404 ACCOUNT_NOT_FOUND`.

## 3. Get account balance

`GET /accounts/{account_id}/balance`

Response `200 OK`:

```json
{ "account_id": "1a93bb57-1035-4e88-bae2-0d7ffc0aa8ef", "balance": 100.00 }
```

Errors: `404 ACCOUNT_NOT_FOUND`.

## 4. Add transaction

`POST /accounts/{account_id}/transactions`

Request:

```json
{
  "type": "credit",
  "amount": 50,
  "description": "Salary",
  "reference_id": "salary-2026-10"
}
```

| Field | Type | Rules |
|---|---|---|
| `type` | string | Required. Exactly `credit` or `debit` (lowercase). |
| `amount` | number | Required. Greater than 0, at most 2 decimal places, at most 17 integer digits. |
| `description` | string | Optional, at most 255 characters. |
| `reference_id` | string | Required, not blank, at most 100 characters. Unique per account. |

Behaviour:

- **credit** adds `amount` to the balance.
- **debit** subtracts `amount` from the balance. It is rejected if the balance is less than `amount`. A debit that brings the balance to exactly 0 is allowed.
- **Duplicate `reference_id`** on the same account: no transaction is created and the balance doesn't change. The API returns `409` with the existing transaction in `existing_transaction`. The same `reference_id` can be used on a different account.
- Checks run in this order: account exists (404), then duplicate reference (409), then sufficient balance (422). A client that retries a debit that already succeeded is therefore told it's a duplicate, not that it's short of funds.
- Inserting the transaction and updating the balance happen in a single database transaction. If either fails, neither is saved.

Response `201 Created`:

```json
{
  "id": "e2d381a0-8cdb-4809-912e-2de718962d01",
  "account_id": "1a93bb57-1035-4e88-bae2-0d7ffc0aa8ef",
  "type": "credit",
  "amount": 50.00,
  "description": "Salary",
  "reference_id": "salary-2026-10",
  "created_at": "2026-10-27T18:06:52.122Z"
}
```

`transfer_id` also appears when the transaction was created by a transfer.

Errors:

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Missing or invalid `type`, `amount`, `reference_id`, `description` (all problems are listed in `details`) |
| 400 | `INVALID_REQUEST_BODY` | Malformed JSON, wrong value type (for example `"amount": "ten"`), or unknown fields |
| 404 | `ACCOUNT_NOT_FOUND` | The account doesn't exist |
| 409 | `DUPLICATE_TRANSACTION` | `reference_id` was already used on this account |
| 422 | `INSUFFICIENT_BALANCE` | The debit is larger than the current balance |

Duplicate example (`409 Conflict`):

```json
{
  "timestamp": "2026-10-27T18:06:52.432Z",
  "status": 409,
  "error": "Conflict",
  "code": "DUPLICATE_TRANSACTION",
  "message": "A transaction with reference_id 'r-1' already exists for account '1a93bb57-...'",
  "path": "/api/v1/accounts/1a93bb57-.../transactions",
  "existing_transaction": { "id": "cfdc934d-...", "type": "credit", "amount": 100.00, "reference_id": "r-1", "...": "..." }
}
```

## 5. Transaction history

`GET /accounts/{account_id}/transactions?type=debit&page=1&limit=20`

| Query param | Default | Rules |
|---|---|---|
| `type` | (none: both types) | `credit` or `debit` |
| `page` | `1` | Integer ≥ 1 (1-based) |
| `limit` | `20` | Integer from 1 to 100 |

Results are sorted latest first by `created_at`. Transactions with the same timestamp are ordered by insertion order, newest first.
A page past the end returns an empty `data` array along with the correct totals.

Response `200 OK`:

```json
{
  "data": [
    {
      "id": "8d74ebbe-18cf-4ff4-b304-a2f716f0713c",
      "account_id": "1a93bb57-1035-4e88-bae2-0d7ffc0aa8ef",
      "type": "debit",
      "amount": 20.00,
      "reference_id": "r-4",
      "created_at": "2026-10-27T18:06:52.318Z"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 1,
    "total_records": 2,
    "total_pages": 2,
    "has_previous": false,
    "has_next": true
  }
}
```

Errors: `400 INVALID_PAGINATION_PARAMETERS` (page < 1, limit out of range, or a value that isn't a number), `400 INVALID_TRANSACTION_TYPE`, `404 ACCOUNT_NOT_FOUND`.

## 6. Transfer (bonus)

`POST /transfers`

Request:

```json
{
  "from_account_id": "1a93bb57-1035-4e88-bae2-0d7ffc0aa8ef",
  "to_account_id": "f813a47f-5911-48ce-83f5-09cec4609de1",
  "amount": 30,
  "description": "Rent share",
  "reference_id": "t-1"
}
```

`amount`, `description` and `reference_id` follow the same rules as for transactions. Both account IDs are required and must be different.

A transfer writes a **debit** on the source and a **credit** on the destination. Both legs share the same `reference_id` and `transfer_id`, and both are written in one database transaction. If either leg fails (an account is missing, the source has insufficient balance, or the reference is already used on either account), nothing is saved.
If `description` is omitted, it defaults to `Transfer from <from> to <to>`.

Response `201 Created`:

```json
{
  "transfer_id": "02ee0360-467a-43d8-9ec5-81c85f7ab3db",
  "from_account_id": "1a93bb57-...",
  "to_account_id": "f813a47f-...",
  "amount": 30.00,
  "reference_id": "t-1",
  "created_at": "2026-10-27T18:06:52.811Z",
  "debit_transaction":  { "id": "ede43f18-...", "type": "debit",  "account_id": "1a93bb57-...", "...": "..." },
  "credit_transaction": { "id": "3f99ab3b-...", "type": "credit", "account_id": "f813a47f-...", "...": "..." }
}
```

Errors: `400 VALIDATION_ERROR`, `400 SAME_ACCOUNT_TRANSFER`, `404 ACCOUNT_NOT_FOUND`, `409 DUPLICATE_TRANSACTION`, `422 INSUFFICIENT_BALANCE`.

---

## Error format

Every error has the same shape:

```json
{
  "timestamp": "2026-10-27T18:06:52.461Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/accounts/1a93.../transactions",
  "details": [
    { "field": "amount", "message": "amount must be greater than 0" },
    { "field": "reference_id", "message": "reference_id is required" },
    { "field": "type", "message": "type must be either 'credit' or 'debit'" }
  ]
}
```

`details` is present only for field validation errors. `existing_transaction` is present only for `DUPLICATE_TRANSACTION`.

| Code | HTTP status |
|---|---|
| `VALIDATION_ERROR` | 400 |
| `INVALID_REQUEST_BODY` | 400 |
| `INVALID_TRANSACTION_TYPE` | 400 |
| `INVALID_PAGINATION_PARAMETERS` | 400 |
| `INVALID_PARAMETER` | 400 |
| `SAME_ACCOUNT_TRANSFER` | 400 |
| `ACCOUNT_NOT_FOUND` | 404 |
| `METHOD_NOT_ALLOWED` | 405 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 |
| `DUPLICATE_TRANSACTION` | 409 |
| `DATA_INTEGRITY_VIOLATION` | 409 |
| `INSUFFICIENT_BALANCE` | 422 |
| `INTERNAL_ERROR` | 500 |
