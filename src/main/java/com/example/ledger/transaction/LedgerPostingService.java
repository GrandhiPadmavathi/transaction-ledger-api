package com.example.ledger.transaction;

import com.example.ledger.account.Account;
import com.example.ledger.common.error.ApiException;
import com.example.ledger.transaction.dto.TransactionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Applies a single ledger entry to an account. Callers must hold the account's row lock
 * (see {@code AccountService#lockForUpdate}) within the same transaction, so the duplicate check,
 * the balance check and the balance update cannot interleave with another posting to that account.
 */
@Service
public class LedgerPostingService {

    private final LedgerTransactionRepository transactions;

    public LedgerPostingService(LedgerTransactionRepository transactions) {
        this.transactions = transactions;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerTransaction post(Account lockedAccount, TransactionType type, BigDecimal amount,
                                  String description, String referenceId, String transferId, Instant now) {
        String accountId = lockedAccount.getId();
        String reference = referenceId.trim();

        transactions.findByAccountIdAndReferenceId(accountId, reference).ifPresent(existing -> {
            throw ApiException.duplicateTransaction(TransactionResponse.from(existing));
        });

        BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (type == TransactionType.CREDIT) {
            lockedAccount.credit(normalized);
        } else {
            if (!lockedAccount.canDebit(normalized)) {
                throw ApiException.insufficientBalance(accountId, lockedAccount.getBalance(), normalized);
            }
            lockedAccount.debit(normalized);
        }

        return transactions.save(LedgerTransaction.create(accountId, type, normalized,
                normalizeDescription(description), reference, transferId, now));
    }

    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
