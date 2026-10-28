package com.example.ledger.account.dto;

import com.example.ledger.account.Account;

import java.math.BigDecimal;

public class BalanceResponse {

    private final String accountId;
    private final BigDecimal balance;

    private BalanceResponse(String accountId, BigDecimal balance) {
        this.accountId = accountId;
        this.balance = balance;
    }

    public static BalanceResponse from(Account account) {
        return new BalanceResponse(account.getId(), account.getBalance());
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}
