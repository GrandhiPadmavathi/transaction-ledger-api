package com.example.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferApiTest extends ApiTestSupport {

    private ResultActions transfer(String from, String to, String amount, String referenceId) throws Exception {
        return postJson("/api/v1/transfers", "{\"from_account_id\":\"" + from + "\",\"to_account_id\":\"" + to
                + "\",\"amount\":" + amount + ",\"reference_id\":\"" + referenceId + "\"}");
    }

    @Test
    void movesMoneyBetweenAccounts() throws Exception {
        String a = createAccount("A");
        String b = createAccount("B");
        credit(a, "100", "seed-a");
        credit(b, "50", "seed-b");

        transfer(a, b, "30", "t-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.from_account_id").value(a))
                .andExpect(jsonPath("$.to_account_id").value(b))
                .andExpect(jsonPath("$.amount").value(30))
                .andExpect(jsonPath("$.debit_transaction.type").value("debit"))
                .andExpect(jsonPath("$.debit_transaction.account_id").value(a))
                .andExpect(jsonPath("$.credit_transaction.type").value("credit"))
                .andExpect(jsonPath("$.credit_transaction.account_id").value(b));

        assertThat(balanceOf(a)).isEqualByComparingTo("70");
        assertThat(balanceOf(b)).isEqualByComparingTo("80");

        mvc.perform(get("/api/v1/accounts/" + a + "/transactions").param("type", "debit"))
                .andExpect(jsonPath("$.data[0].reference_id").value("t-1"))
                .andExpect(jsonPath("$.data[0].transfer_id").isNotEmpty());
    }

    @Test
    void insufficientBalanceRollsBackEntireTransfer() throws Exception {
        String a = createAccount("A");
        String b = createAccount("B");
        credit(a, "20", "seed");

        transfer(a, b, "30", "t-1")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));

        assertThat(balanceOf(a)).isEqualByComparingTo("20");
        assertThat(balanceOf(b)).isEqualByComparingTo("0");
        mvc.perform(get("/api/v1/accounts/" + b + "/transactions"))
                .andExpect(jsonPath("$.pagination.total_records").value(0));
    }

    @Test
    void failureOnCreditLegRollsBackDebitLeg() throws Exception {
        String a = createAccount("A");
        String b = createAccount("B");
        credit(a, "100", "seed");
        credit(b, "1", "t-1");

        // The debit on A succeeds first, then the credit on B hits a duplicate reference.
        transfer(a, b, "30", "t-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_TRANSACTION"));

        assertThat(balanceOf(a)).isEqualByComparingTo("100");
        assertThat(balanceOf(b)).isEqualByComparingTo("1");
        mvc.perform(get("/api/v1/accounts/" + a + "/transactions"))
                .andExpect(jsonPath("$.pagination.total_records").value(1));
    }

    @Test
    void bothAccountsMustExist() throws Exception {
        String a = createAccount("A");
        credit(a, "100", "seed");

        transfer(a, "missing", "10", "t-1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        transfer("missing", a, "10", "t-2")
                .andExpect(status().isNotFound());

        assertThat(balanceOf(a)).isEqualByComparingTo("100");
    }

    @Test
    void duplicateTransferIsRejected() throws Exception {
        String a = createAccount("A");
        String b = createAccount("B");
        credit(a, "100", "seed");

        transfer(a, b, "10", "t-1").andExpect(status().isCreated());
        transfer(a, b, "10", "t-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_TRANSACTION"));

        assertThat(balanceOf(a)).isEqualByComparingTo("90");
        assertThat(balanceOf(b)).isEqualByComparingTo("10");
    }

    @Test
    void rejectsTransferToSameAccountAndInvalidInput() throws Exception {
        String a = createAccount("A");
        credit(a, "100", "seed");

        transfer(a, a, "10", "t-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT_TRANSFER"));
        transfer(a, "other", "0", "t-2")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        postJson("/api/v1/transfers", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(4));

        assertThat(balanceOf(a)).isEqualByComparingTo("100");
    }
}
