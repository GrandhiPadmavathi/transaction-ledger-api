package com.example.ledger.account.dto;

import com.example.ledger.account.Account;

import java.math.BigDecimal;
import java.time.Instant;

public class AccountResponse {

    private final String id;
    private final String name;
    private final BigDecimal balance;
    private final Instant createdAt;

    private AccountResponse(String id, String name, BigDecimal balance, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.balance = balance;
        this.createdAt = createdAt;
    }

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getName(), account.getBalance(), account.getCreatedAt());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
