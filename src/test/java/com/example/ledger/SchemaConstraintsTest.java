package com.example.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the integrity rules are enforced by the database itself, not only by the application. */
@SpringBootTest
@ActiveProfiles("test")
class SchemaConstraintsTest {

    @Autowired
    private JdbcTemplate jdbc;

    private String accountId;

    @BeforeEach
    void createAccount() {
        accountId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO accounts (id, name, balance, created_at) VALUES (?, 'Raw', 10, ?)",
                accountId, now());
    }

    @Test
    void balanceCannotBecomeNegative() {
        assertThatThrownBy(() -> jdbc.update("UPDATE accounts SET balance = -1 WHERE id = ?", accountId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void accountNameCannotBeBlank() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO accounts (id, name, balance, created_at) VALUES (?, '  ', 0, ?)",
                UUID.randomUUID().toString(), now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void referenceIdIsUniquePerAccount() {
        insertTransaction(accountId, "CREDIT", "5", "ref");
        assertThatThrownBy(() -> insertTransaction(accountId, "CREDIT", "5", "ref"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void amountMustBePositive() {
        assertThatThrownBy(() -> insertTransaction(accountId, "CREDIT", "0", "zero"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void typeMustBeCreditOrDebit() {
        assertThatThrownBy(() -> insertTransaction(accountId, "REFUND", "5", "bad-type"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void transactionMustReferenceExistingAccount() {
        assertThatThrownBy(() -> insertTransaction("no-such-account", "CREDIT", "5", "orphan"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertTransaction(String account, String type, String amount, String reference) {
        jdbc.update("INSERT INTO transactions (id, account_id, type, amount, reference_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), account, type, new java.math.BigDecimal(amount), reference, now());
    }

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }
}
