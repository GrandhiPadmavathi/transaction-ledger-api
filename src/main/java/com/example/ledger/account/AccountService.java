package com.example.ledger.account;

import com.example.ledger.common.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final Clock clock;

    public AccountService(AccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
    public Account create(String name) {
        return accounts.save(Account.open(name.trim(), Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public Account get(String accountId) {
        return accounts.findById(accountId).orElseThrow(() -> ApiException.accountNotFound(accountId));
    }

    @Transactional(readOnly = true)
    public void requireExists(String accountId) {
        if (!accounts.existsById(accountId)) {
            throw ApiException.accountNotFound(accountId);
        }
    }

    /** Must be called inside an active transaction; the lock is released when that transaction ends. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Account lockForUpdate(String accountId) {
        return accounts.findByIdForUpdate(accountId).orElseThrow(() -> ApiException.accountNotFound(accountId));
    }
}
