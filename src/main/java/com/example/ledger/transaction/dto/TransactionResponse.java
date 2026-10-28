package com.example.ledger.transaction.dto;

import com.example.ledger.transaction.LedgerTransaction;
import com.example.ledger.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public class TransactionResponse {

    private final String id;
    private final String accountId;
    private final TransactionType type;
    private final BigDecimal amount;
    private final String description;
    private final String referenceId;
    private final String transferId;
    private final Instant createdAt;

    private TransactionResponse(LedgerTransaction tx) {
        this.id = tx.getId();
        this.accountId = tx.getAccountId();
        this.type = tx.getType();
        this.amount = tx.getAmount();
        this.description = tx.getDescription();
        this.referenceId = tx.getReferenceId();
        this.transferId = tx.getTransferId();
        this.createdAt = tx.getCreatedAt();
    }

    public static TransactionResponse from(LedgerTransaction tx) {
        return new TransactionResponse(tx);
    }

    public String getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getTransferId() {
        return transferId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
