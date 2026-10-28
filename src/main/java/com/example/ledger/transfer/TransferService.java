package com.example.ledger.transfer;

import com.example.ledger.account.Account;
import com.example.ledger.account.AccountService;
import com.example.ledger.common.error.ApiException;
import com.example.ledger.common.error.ErrorCode;
import com.example.ledger.transaction.LedgerPostingService;
import com.example.ledger.transaction.LedgerTransaction;
import com.example.ledger.transaction.TransactionType;
import com.example.ledger.transfer.dto.TransferRequest;
import com.example.ledger.transfer.dto.TransferResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountService accountService;
    private final LedgerPostingService postingService;
    private final Clock clock;

    public TransferService(AccountService accountService, LedgerPostingService postingService, Clock clock) {
        this.accountService = accountService;
        this.postingService = postingService;
        this.clock = clock;
    }

    /**
     * Debits the source and credits the destination in one database transaction. Any failure
     * (missing account, duplicate reference, insufficient balance) rolls back both legs.
     */
    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        String fromId = request.getFromAccountId().trim();
        String toId = request.getToAccountId().trim();
        if (fromId.equals(toId)) {
            throw new ApiException(ErrorCode.SAME_ACCOUNT_TRANSFER,
                    "from_account_id and to_account_id must be different accounts");
        }

        // Lock both rows in a fixed (id) order so two opposite transfers cannot deadlock.
        boolean fromFirst = fromId.compareTo(toId) < 0;
        Account first = accountService.lockForUpdate(fromFirst ? fromId : toId);
        Account second = accountService.lockForUpdate(fromFirst ? toId : fromId);
        Account source = fromFirst ? first : second;
        Account destination = fromFirst ? second : first;

        String transferId = UUID.randomUUID().toString();
        Instant now = Instant.now(clock);
        String description = request.getDescription() != null
                ? request.getDescription()
                : "Transfer from " + fromId + " to " + toId;

        LedgerTransaction debit = postingService.post(source, TransactionType.DEBIT, request.getAmount(),
                description, request.getReferenceId(), transferId, now);
        LedgerTransaction credit = postingService.post(destination, TransactionType.CREDIT, request.getAmount(),
                description, request.getReferenceId(), transferId, now);
        return TransferResponse.of(debit, credit);
    }
}
