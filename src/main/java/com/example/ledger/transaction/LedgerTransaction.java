package com.example.ledger.transaction;

import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.PostLoad;
import javax.persistence.PostPersist;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A ledger entry. Rows are insert-only: Hibernate never issues UPDATE statements for this entity
 * and the API exposes no endpoint that modifies or deletes it.
 */
@Entity
@Immutable
@Table(name = "transactions")
public class LedgerTransaction implements Persistable<String> {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    /** Database-assigned insertion order; breaks ties between transactions with the same created_at. */
    @Column(name = "entry_no", insertable = false, updatable = false)
    private Long entryNo;

    @Column(name = "account_id", length = 36, nullable = false, updatable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 6, nullable = false, updatable = false)
    private TransactionType type;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "description", updatable = false)
    private String description;

    @Column(name = "reference_id", length = 100, nullable = false, updatable = false)
    private String referenceId;

    @Column(name = "transfer_id", length = 36, updatable = false)
    private String transferId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew;

    protected LedgerTransaction() {
    }

    static LedgerTransaction create(String accountId, TransactionType type, BigDecimal amount, String description,
                                    String referenceId, String transferId, Instant createdAt) {
        LedgerTransaction tx = new LedgerTransaction();
        tx.id = UUID.randomUUID().toString();
        tx.accountId = accountId;
        tx.type = type;
        tx.amount = amount;
        tx.description = description;
        tx.referenceId = referenceId;
        tx.transferId = transferId;
        tx.createdAt = createdAt;
        tx.isNew = true;
        return tx;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        isNew = false;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
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
