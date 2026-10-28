# Assumptions

Decisions made where the assignment left room for interpretation.

1. **Identifiers** are server-generated UUID strings. An unknown or malformed account ID returns `404 ACCOUNT_NOT_FOUND`.
2. **Read-only fields on create.** Clients may only send `name` when creating an account. Sending `balance`, `id` or `created_at` (or any unknown field on any endpoint) is rejected with `400`, rather than silently ignored, so a client can never believe it set an opening balance.
3. **Transaction endpoint shape.** Transactions are created under the account (`POST /accounts/{account_id}/transactions`), so `account_id` comes from the path instead of the body.
4. **Transaction type** must be lowercase `credit` or `debit`, matching the assignment text. Other values, including `CREDIT`, are rejected.
5. **Amounts** must be greater than 0, with at most 2 decimal places (a currency with minor units such as cents) and at most 17 integer digits. A single currency is assumed; there is no currency field.
6. **Duplicate handling.** A repeated `reference_id` on the same account returns `409 Conflict`, and the response includes the original transaction in `existing_transaction`. This applies even if the new payload differs (for example, a different amount); the reference is what identifies the operation. The duplicate check runs before the balance check, so a retried debit is reported as a duplicate.
7. **Rejected transactions are not stored.** A debit rejected for insufficient balance doesn't consume its `reference_id`; the client can reuse it once funds are available.
8. **`description`** is optional. Blank descriptions are stored as empty (`null`) and omitted from responses.
9. **Pagination** is 1-based. `limit` defaults to 20 with a maximum of 100. Requesting a page past the end returns an empty list with correct totals rather than an error.
10. **Ordering.** Transactions are sorted by `created_at` descending. An internal, database-generated insertion counter (`entry_no`) breaks ties when two transactions share the same timestamp. The timestamp is taken after the account lock is acquired, so the order always matches the order in which postings were applied.
11. **Transfers** use one `reference_id` for both legs and link them with a shared `transfer_id`. The reference must be unused on both accounts. Transfers to the same account are rejected with `400`.
12. **Authentication and authorization** are out of scope for this assignment.
13. **Database.** H2 was chosen so the project runs with zero setup. The schema uses standard SQL and pessimistic locking, so switching to PostgreSQL or MySQL needs only a datasource change and the JDBC driver dependency.
