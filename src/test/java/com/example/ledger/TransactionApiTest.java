package com.example.ledger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionApiTest extends ApiTestSupport {

    @Test
    void creditIncreasesBalance() throws Exception {
        String id = createAccount("Credit");
        credit(id, "100", "c-1");

        postTransaction(id, "credit", "50", "c-2")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.account_id").value(id))
                .andExpect(jsonPath("$.type").value("credit"))
                .andExpect(jsonPath("$.amount").value(50))
                .andExpect(jsonPath("$.description").value("credit 50"))
                .andExpect(jsonPath("$.reference_id").value("c-2"))
                .andExpect(jsonPath("$.created_at", notNullValue()));

        assertThat(balanceOf(id)).isEqualByComparingTo("150");
    }

    @Test
    void debitDecreasesBalance() throws Exception {
        String id = createAccount("Debit");
        credit(id, "100", "c-1");

        postTransaction(id, "debit", "30", "d-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("debit"));

        assertThat(balanceOf(id)).isEqualByComparingTo("70");
    }

    @Test
    void debitIsRejectedWhenBalanceIsInsufficient() throws Exception {
        String id = createAccount("Poor");
        credit(id, "100", "c-1");

        postTransaction(id, "debit", "150", "d-1")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));

        assertThat(balanceOf(id)).isEqualByComparingTo("100");
        // The rejected reference was not persisted, so it can be reused for a valid debit.
        postTransaction(id, "debit", "100", "d-1").andExpect(status().isCreated());
        assertThat(balanceOf(id)).isEqualByComparingTo("0");
    }

    @Test
    void debitOfExactBalanceIsAllowed() throws Exception {
        String id = createAccount("Exact");
        credit(id, "10.25", "c-1");
        postTransaction(id, "debit", "10.25", "d-1").andExpect(status().isCreated());
        assertThat(balanceOf(id)).isEqualByComparingTo("0");
    }

    @Test
    void balanceReflectsAllValidTransactions() throws Exception {
        String id = createAccount("Example");
        credit(id, "100", "r-1");
        credit(id, "50", "r-2");
        postTransaction(id, "debit", "30", "r-3").andExpect(status().isCreated());
        postTransaction(id, "debit", "20", "r-4").andExpect(status().isCreated());
        postTransaction(id, "debit", "1000", "r-5").andExpect(status().isUnprocessableEntity());

        assertThat(balanceOf(id)).isEqualByComparingTo("100");
    }

    @Test
    void duplicateReferenceDoesNotCreateTransactionOrChangeBalance() throws Exception {
        String id = createAccount("Dup");
        String originalId = read(postTransaction(id, "credit", "100", "ref-1")
                .andExpect(status().isCreated()).andReturn()).get("id").asText();

        postTransaction(id, "credit", "100", "ref-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_TRANSACTION"))
                .andExpect(jsonPath("$.existing_transaction.id").value(originalId));
        postTransaction(id, "debit", "40", "ref-1")
                .andExpect(status().isConflict());

        assertThat(balanceOf(id)).isEqualByComparingTo("100");
        mvc.perform(get("/api/v1/accounts/" + id + "/transactions"))
                .andExpect(jsonPath("$.pagination.total_records").value(1));
    }

    @Test
    void duplicateIsReportedBeforeInsufficientBalance() throws Exception {
        String id = createAccount("Retry");
        credit(id, "50", "c-1");
        postTransaction(id, "debit", "50", "d-1").andExpect(status().isCreated());

        // A client retrying a debit that already succeeded must be told it is a duplicate.
        postTransaction(id, "debit", "50", "d-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_TRANSACTION"));
    }

    @Test
    void sameReferenceIsAllowedOnDifferentAccounts() throws Exception {
        String a = createAccount("A");
        String b = createAccount("B");
        postTransaction(a, "credit", "10", "shared").andExpect(status().isCreated());
        postTransaction(b, "credit", "20", "shared").andExpect(status().isCreated());

        assertThat(balanceOf(a)).isEqualByComparingTo("10");
        assertThat(balanceOf(b)).isEqualByComparingTo("20");
    }

    @Test
    void unknownAccountReturnsNotFound() throws Exception {
        postTransaction("missing-account", "credit", "10", "r-1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void invalidTypeIsRejected() throws Exception {
        String id = createAccount("Type");
        postTransaction(id, "refund", "10", "r-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[*].field", hasItem("type")));
        postTransaction(id, "CREDIT", "10", "r-2")
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidAmountsAreRejected() throws Exception {
        String id = createAccount("Amount");
        for (String amount : new String[]{"0", "-5", "0.001", "123456789012345678"}) {
            postTransaction(id, "credit", amount, "r-" + amount)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[*].field", hasItem("amount")));
        }
        postTransaction(id, "credit", null, "r-null")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("amount")));
        postJson("/api/v1/accounts/" + id + "/transactions",
                "{\"type\":\"credit\",\"amount\":\"ten\",\"reference_id\":\"r\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.message").value("Field 'amount' has an invalid value or type"));

        assertThat(balanceOf(id)).isEqualByComparingTo("0");
    }

    @Test
    void referenceIdIsRequired() throws Exception {
        String id = createAccount("Ref");
        postTransaction(id, "credit", "10", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("reference_id"));
        postTransaction(id, "credit", "10", "  ")
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsAllInvalidFieldsTogether() throws Exception {
        String id = createAccount("Many");
        postJson("/api/v1/accounts/" + id + "/transactions", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(3));
    }

    @Test
    void transactionsCannotBeModifiedOrDeleted() throws Exception {
        String id = createAccount("Immutable");
        String txId = read(postTransaction(id, "credit", "10", "r-1").andReturn()).get("id").asText();
        String base = "/api/v1/accounts/" + id + "/transactions";

        mvc.perform(put(base).contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(delete(base)).andExpect(status().isMethodNotAllowed());
        mvc.perform(patch(base).contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(base + "/" + txId)).andExpect(status().is4xxClientError());
        mvc.perform(put(base + "/" + txId).contentType("application/json").content("{\"amount\":1}"))
                .andExpect(status().is4xxClientError());

        assertThat(balanceOf(id)).isEqualByComparingTo("10");
    }
}
