package com.example.ledger.transaction;

import com.example.ledger.common.error.ApiException;
import com.example.ledger.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TransactionType {
    CREDIT("credit"),
    DEBIT("debit");

    private final String value;

    TransactionType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static TransactionType fromValue(String value) {
        for (TransactionType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new ApiException(ErrorCode.INVALID_TRANSACTION_TYPE,
                "Transaction type must be either 'credit' or 'debit', but was '" + value + "'");
    }
}
