package com.example.ledger.common.error;

import com.example.ledger.transaction.dto.TransactionResponse;

import java.math.BigDecimal;

public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient TransactionResponse existingTransaction;

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    private ApiException(ErrorCode code, String message, TransactionResponse existingTransaction) {
        super(message);
        this.code = code;
        this.existingTransaction = existingTransaction;
    }

    public static ApiException accountNotFound(String accountId) {
        return new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "Account '" + accountId + "' does not exist");
    }

    public static ApiException duplicateTransaction(TransactionResponse existing) {
        return new ApiException(ErrorCode.DUPLICATE_TRANSACTION,
                "A transaction with reference_id '" + existing.getReferenceId()
                        + "' already exists for account '" + existing.getAccountId() + "'",
                existing);
    }

    public static ApiException insufficientBalance(String accountId, BigDecimal balance, BigDecimal amount) {
        return new ApiException(ErrorCode.INSUFFICIENT_BALANCE,
                "Account '" + accountId + "' has insufficient balance: available " + balance.toPlainString()
                        + ", requested " + amount.toPlainString());
    }

    public ErrorCode getCode() {
        return code;
    }

    public TransactionResponse getExistingTransaction() {
        return existingTransaction;
    }
}
