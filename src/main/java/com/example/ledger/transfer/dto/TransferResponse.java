package com.example.ledger.transfer.dto;

import com.example.ledger.transaction.LedgerTransaction;
import com.example.ledger.transaction.dto.TransactionResponse;

import java.math.BigDecimal;
import java.time.Instant;

public class TransferResponse {

    private final String transferId;
    private final String fromAccountId;
    private final String toAccountId;
    private final BigDecimal amount;
    private final String referenceId;
    private final Instant createdAt;
    private final TransactionResponse debitTransaction;
    private final TransactionResponse creditTransaction;

    private TransferResponse(LedgerTransaction debit, LedgerTransaction credit) {
        this.transferId = debit.getTransferId();
        this.fromAccountId = debit.getAccountId();
        this.toAccountId = credit.getAccountId();
        this.amount = debit.getAmount();
        this.referenceId = debit.getReferenceId();
        this.createdAt = debit.getCreatedAt();
        this.debitTransaction = TransactionResponse.from(debit);
        this.creditTransaction = TransactionResponse.from(credit);
    }

    public static TransferResponse of(LedgerTransaction debit, LedgerTransaction credit) {
        return new TransferResponse(debit, credit);
    }

    public String getTransferId() {
        return transferId;
    }

    public String getFromAccountId() {
        return fromAccountId;
    }

    public String getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public TransactionResponse getDebitTransaction() {
        return debitTransaction;
    }

    public TransactionResponse getCreditTransaction() {
        return creditTransaction;
    }
}
