package com.example.ledger.transfer.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

public class TransferRequest {

    @NotBlank(message = "from_account_id is required")
    private String fromAccountId;

    @NotBlank(message = "to_account_id is required")
    private String toAccountId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0", inclusive = false, message = "amount must be greater than 0")
    @Digits(integer = 17, fraction = 2, message = "amount must have at most 17 integer digits and 2 decimal places")
    private BigDecimal amount;

    @Size(max = 255, message = "description must be at most 255 characters")
    private String description;

    @NotBlank(message = "reference_id is required")
    @Size(max = 100, message = "reference_id must be at most 100 characters")
    private String referenceId;

    public String getFromAccountId() {
        return fromAccountId;
    }

    public void setFromAccountId(String fromAccountId) {
        this.fromAccountId = fromAccountId;
    }

    public String getToAccountId() {
        return toAccountId;
    }

    public void setToAccountId(String toAccountId) {
        this.toAccountId = toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }
}
