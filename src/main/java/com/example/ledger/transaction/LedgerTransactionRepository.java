package com.example.ledger.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/** Deliberately exposes no update or delete operations: ledger entries are append-only. */
public interface LedgerTransactionRepository extends Repository<LedgerTransaction, String> {

    LedgerTransaction save(LedgerTransaction transaction);

    Optional<LedgerTransaction> findByAccountIdAndReferenceId(String accountId, String referenceId);

    Page<LedgerTransaction> findByAccountId(String accountId, Pageable pageable);

    Page<LedgerTransaction> findByAccountIdAndType(String accountId, TransactionType type, Pageable pageable);
}
