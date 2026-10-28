package com.example.ledger.common.error;

import com.example.ledger.transaction.dto.TransactionResponse;

import java.time.Instant;
import java.util.List;

public class ErrorResponse {

    private final Instant timestamp;
    private final int status;
    private final String error;
    private final String code;
    private final String message;
    private final String path;
    private final List<FieldViolation> details;
    private final TransactionResponse existingTransaction;

    public ErrorResponse(Instant timestamp, ErrorCode code, String message, String path,
                         List<FieldViolation> details, TransactionResponse existingTransaction) {
        this.timestamp = timestamp;
        this.status = code.status().value();
        this.error = code.status().getReasonPhrase();
        this.code = code.name();
        this.message = message;
        this.path = path;
        this.details = details;
        this.existingTransaction = existingTransaction;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public List<FieldViolation> getDetails() {
        return details;
    }

    public TransactionResponse getExistingTransaction() {
        return existingTransaction;
    }

    public static class FieldViolation {
        private final String field;
        private final String message;

        public FieldViolation(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }
    }
}
