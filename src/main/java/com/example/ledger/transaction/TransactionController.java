package com.example.ledger.transaction;

import com.example.ledger.common.PageResponse;
import com.example.ledger.transaction.dto.CreateTransactionRequest;
import com.example.ledger.transaction.dto.TransactionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@PathVariable String accountId,
                                      @Valid @RequestBody CreateTransactionRequest request) {
        return TransactionResponse.from(transactionService.record(accountId, request));
    }

    @GetMapping
    public PageResponse<TransactionResponse> history(
            @PathVariable String accountId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "" + TransactionService.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = "" + TransactionService.DEFAULT_LIMIT) int limit) {
        return PageResponse.of(transactionService.history(accountId, type, page, limit), TransactionResponse::from);
    }
}
