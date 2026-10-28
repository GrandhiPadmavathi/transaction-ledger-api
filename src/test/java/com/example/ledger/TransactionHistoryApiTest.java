package com.example.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionHistoryApiTest extends ApiTestSupport {

    private String accountId;

    /** Creates, in order: c1 (credit 100), c2 (credit 50), d1 (debit 30), c3 (credit 10), d2 (debit 20). */
    @BeforeEach
    void seed() throws Exception {
        accountId = createAccount("History");
        credit(accountId, "100", "c1");
        credit(accountId, "50", "c2");
        postTransaction(accountId, "debit", "30", "d1").andExpect(status().isCreated());
        credit(accountId, "10", "c3");
        postTransaction(accountId, "debit", "20", "d2").andExpect(status().isCreated());
    }

    private String url() {
        return "/api/v1/accounts/" + accountId + "/transactions";
    }

    @Test
    void returnsAllTransactionsLatestFirstWithDefaultPagination() throws Exception {
        mvc.perform(get(url()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].reference_id", contains("d2", "c3", "d1", "c2", "c1")))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.limit").value(20))
                .andExpect(jsonPath("$.pagination.total_records").value(5))
                .andExpect(jsonPath("$.pagination.total_pages").value(1))
                .andExpect(jsonPath("$.pagination.has_next").value(false))
                .andExpect(jsonPath("$.pagination.has_previous").value(false));
    }

    @Test
    void filtersByType() throws Exception {
        mvc.perform(get(url()).param("type", "credit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].reference_id", contains("c3", "c2", "c1")))
                .andExpect(jsonPath("$.data[*].type", everyItem(is("credit"))))
                .andExpect(jsonPath("$.pagination.total_records").value(3));
        mvc.perform(get(url()).param("type", "debit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].reference_id", contains("d2", "d1")))
                .andExpect(jsonPath("$.pagination.total_records").value(2));
    }

    @Test
    void paginates() throws Exception {
        mvc.perform(get(url()).param("page", "1").param("limit", "2"))
                .andExpect(jsonPath("$.data[*].reference_id", contains("d2", "c3")))
                .andExpect(jsonPath("$.pagination.total_pages").value(3))
                .andExpect(jsonPath("$.pagination.has_next").value(true));
        mvc.perform(get(url()).param("page", "3").param("limit", "2"))
                .andExpect(jsonPath("$.data[*].reference_id", contains("c1")))
                .andExpect(jsonPath("$.pagination.page").value(3))
                .andExpect(jsonPath("$.pagination.has_next").value(false))
                .andExpect(jsonPath("$.pagination.has_previous").value(true));
        mvc.perform(get(url()).param("page", "9").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.pagination.total_records").value(5));
    }

    @Test
    void paginatesWithinFilter() throws Exception {
        mvc.perform(get(url()).param("type", "credit").param("page", "2").param("limit", "2"))
                .andExpect(jsonPath("$.data[*].reference_id", contains("c1")))
                .andExpect(jsonPath("$.pagination.total_records").value(3))
                .andExpect(jsonPath("$.pagination.total_pages").value(2));
    }

    @Test
    void rejectsInvalidPaginationParameters() throws Exception {
        String[][] invalid = {{"page", "0"}, {"page", "-1"}, {"page", "abc"}, {"limit", "0"}, {"limit", "101"},
                {"limit", "x"}};
        for (String[] param : invalid) {
            mvc.perform(get(url()).param(param[0], param[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PAGINATION_PARAMETERS"));
        }
    }

    @Test
    void rejectsInvalidTypeFilter() throws Exception {
        mvc.perform(get(url()).param("type", "refund"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_TYPE"));
    }

    @Test
    void returnsNotFoundForUnknownAccount() throws Exception {
        mvc.perform(get("/api/v1/accounts/unknown/transactions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void returnsEmptyPageForAccountWithoutTransactions() throws Exception {
        String empty = createAccount("Empty");
        mvc.perform(get("/api/v1/accounts/" + empty + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.pagination.total_records").value(0))
                .andExpect(jsonPath("$.pagination.total_pages").value(0));
    }
}
