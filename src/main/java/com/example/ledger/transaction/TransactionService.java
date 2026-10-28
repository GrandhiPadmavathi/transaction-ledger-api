package com.example.ledger.transaction;

import com.example.ledger.account.Account;
import com.example.ledger.account.AccountService;
import com.example.ledger.common.error.ApiException;
import com.example.ledger.common.error.ErrorCode;
import com.example.ledger.transaction.dto.CreateTransactionRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class TransactionService {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("entryNo"));

    private final AccountService accountService;
    private final LedgerPostingService postingService;
    private final LedgerTransactionRepository transactions;
    private final Clock clock;

    public TransactionService(AccountService accountService, LedgerPostingService postingService,
                              LedgerTransactionRepository transactions, Clock clock) {
        this.accountService = accountService;
        this.postingService = postingService;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Transactional
    public LedgerTransaction record(String accountId, CreateTransactionRequest request) {
        TransactionType type = TransactionType.fromValue(request.getType());
        Account account = accountService.lockForUpdate(accountId);
        // Read the clock only once the lock is held, so created_at order matches commit order per account.
        Instant now = Instant.now(clock);
        return postingService.post(account, type, request.getAmount(), request.getDescription(),
                request.getReferenceId(), null, now);
    }

    @Transactional(readOnly = true)
    public Page<LedgerTransaction> history(String accountId, String type, int page, int limit) {
        if (page < 1) {
            throw new ApiException(ErrorCode.INVALID_PAGINATION_PARAMETERS, "page must be greater than or equal to 1");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ApiException(ErrorCode.INVALID_PAGINATION_PARAMETERS,
                    "limit must be between 1 and " + MAX_LIMIT);
        }
        TransactionType typeFilter = (type == null || type.isEmpty()) ? null : TransactionType.fromValue(type);
        accountService.requireExists(accountId);

        PageRequest pageRequest = PageRequest.of(page - 1, limit, LATEST_FIRST);
        return typeFilter == null
                ? transactions.findByAccountId(accountId, pageRequest)
                : transactions.findByAccountIdAndType(accountId, typeFilter, pageRequest);
    }
}
