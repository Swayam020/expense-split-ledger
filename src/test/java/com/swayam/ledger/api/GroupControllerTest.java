package com.swayam.ledger.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.swayam.ledger.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GroupControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void createsAGroupSplitsAnExpenseAndReportsBalances() throws Exception {
        String created = mvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Goa","currency":"INR","members":["Alice","Bob","Carol"]}"""))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.members.length()").value(3))
                .andReturn().getResponse().getContentAsString();

        JsonNode group = json.readTree(created);
        String groupId = group.get("id").asText();
        String aliceId = group.get("members").get(0).get("id").asText();

        mvc.perform(post("/api/groups/" + groupId + "/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payerId":"%s","description":"Dinner","amount":100.00}""".formatted(aliceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shares.length()").value(3));

        String balances = mvc.perform(get("/api/groups/" + groupId + "/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(balances).contains("66.66").contains("-33.33");
    }

    @Test
    void rejectsANegativeAmountWithFourHundred() throws Exception {
        String created = mvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cab","currency":"INR","members":["Alice","Bob"]}"""))
                .andReturn().getResponse().getContentAsString();
        JsonNode group = json.readTree(created);

        mvc.perform(post("/api/groups/" + group.get("id").asText() + "/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payerId":"%s","description":"Refund","amount":-5.00}"""
                                .formatted(group.get("members").get(0).get("id").asText())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("amount")));
    }

    @Test
    void rejectsAMalformedCurrencyCode() throws Exception {
        mvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bad","currency":"RUPEES","members":["Alice"]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void returnsFourOhFourForAGroupThatDoesNotExist() throws Exception {
        mvc.perform(get("/api/groups/" + java.util.UUID.randomUUID() + "/balances"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no such group")));
    }

    @Test
    void rejectsAPayerWhoIsNotInTheGroup() throws Exception {
        String created = mvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Closed","currency":"INR","members":["Alice"]}"""))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/groups/" + json.readTree(created).get("id").asText() + "/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payerId":"%s","description":"Nope","amount":10.00}"""
                                .formatted(java.util.UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity());
    }
}
