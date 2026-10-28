package com.example.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String createAccount(String name) throws Exception {
        MvcResult result = postJson("/api/v1/accounts", "{\"name\":\"" + name + "\"}")
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("id").asText();
    }

    protected ResultActions postTransaction(String accountId, String type, String amount, String referenceId)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("amount", amount == null ? null : new BigDecimal(amount));
        body.put("description", type + " " + amount);
        body.put("reference_id", referenceId);
        return postJson("/api/v1/accounts/" + accountId + "/transactions", objectMapper.writeValueAsString(body));
    }

    protected void credit(String accountId, String amount, String referenceId) throws Exception {
        postTransaction(accountId, "credit", amount, referenceId).andExpect(status().isCreated());
    }

    protected BigDecimal balanceOf(String accountId) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/accounts/" + accountId + "/balance"))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).get("balance").decimalValue();
    }

    protected ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
