package com.example.ledger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountApiTest extends ApiTestSupport {

    @Test
    void createsAccountWithZeroBalanceAndSystemTimestamp() throws Exception {
        postJson("/api/v1/accounts", "{\"name\":\"  Alice  \"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("http://localhost/api/v1/accounts/")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.created_at", notNullValue()));
    }

    @Test
    void generatesUniqueIds() throws Exception {
        assertThat(createAccount("A")).isNotEqualTo(createAccount("B"));
    }

    @Test
    void rejectsClientSuppliedBalance() throws Exception {
        postJson("/api/v1/accounts", "{\"name\":\"Mallory\",\"balance\":1000}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.message").value(
                        "Unknown or read-only field 'balance' is not allowed in the request body"));
    }

    @Test
    void rejectsClientSuppliedIdAndTimestamp() throws Exception {
        postJson("/api/v1/accounts", "{\"name\":\"Mallory\",\"id\":\"mine\"}")
                .andExpect(status().isBadRequest());
        postJson("/api/v1/accounts", "{\"name\":\"Mallory\",\"created_at\":\"2020-01-01T00:00:00Z\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresName() throws Exception {
        postJson("/api/v1/accounts", "{\"name\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message").value("name is required"));
        postJson("/api/v1/accounts", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsMalformedJsonAndWrongContentType() throws Exception {
        postJson("/api/v1/accounts", "{\"name\":")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
        mvc.perform(post("/api/v1/accounts").contentType("text/plain").content("name=x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void returnsAccountAndBalance() throws Exception {
        String id = createAccount("Bob");
        credit(id, "42.50", "seed");

        mvc.perform(get("/api/v1/accounts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Bob"))
                .andExpect(jsonPath("$.balance").value(42.5));
        mvc.perform(get("/api/v1/accounts/" + id + "/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_id").value(id))
                .andExpect(jsonPath("$.balance").value(42.5));
    }

    @Test
    void returnsNotFoundForUnknownAccount() throws Exception {
        mvc.perform(get("/api/v1/accounts/does-not-exist/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Account 'does-not-exist' does not exist"))
                .andExpect(jsonPath("$.path").value("/api/v1/accounts/does-not-exist/balance"));
        mvc.perform(get("/api/v1/accounts/does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
