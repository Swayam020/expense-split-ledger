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

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SettlementControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void settlesAndReturnsTwoOhOneCreated() throws Exception {
        JsonNode group = createGroup("Trip", "Alice", "Bob");
        String groupId = group.get("id").asText();
        String aliceId = group.get("members").get(0).get("id").asText();
        String bobId = group.get("members").get(1).get("id").asText();

        mvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .header("Idempotency-Key", "http-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMemberId":"%s","toMemberId":"%s","amount":10.00}"""
                                .formatted(bobId, aliceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromMemberId").value(bobId))
                .andExpect(jsonPath("$.toMemberId").value(aliceId));
    }

    @Test
    void replayingTheSameKeyReturnsTheSameSettlementIdAndStillTwoOhOne() throws Exception {
        JsonNode group = createGroup("Replay", "Alice", "Bob");
        String groupId = group.get("id").asText();
        String aliceId = group.get("members").get(0).get("id").asText();
        String bobId = group.get("members").get(1).get("id").asText();
        String body = """
                {"fromMemberId":"%s","toMemberId":"%s","amount":10.00}""".formatted(bobId, aliceId);

        String first = mvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .header("Idempotency-Key", "http-replay-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String second = mvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .header("Idempotency-Key", "http-replay-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(json.readTree(second).get("id"))
                .isEqualTo(json.readTree(first).get("id"));
    }

    @Test
    void aReusedKeyWithADifferentBodyIsFourOhNine() throws Exception {
        JsonNode group = createGroup("Conflict", "Alice", "Bob");
        String groupId = group.get("id").asText();
        String aliceId = group.get("members").get(0).get("id").asText();
        String bobId = group.get("members").get(1).get("id").asText();

        mvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .header("Idempotency-Key", "conflict-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMemberId":"%s","toMemberId":"%s","amount":10.00}"""
                                .formatted(bobId, aliceId)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .header("Idempotency-Key", "conflict-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMemberId":"%s","toMemberId":"%s","amount":99.00}"""
                                .formatted(bobId, aliceId)))
                .andExpect(status().isConflict());
    }

    @Test
    void aMissingIdempotencyKeyHeaderIsFourHundred() throws Exception {
        JsonNode group = createGroup("NoKey", "Alice", "Bob");
        String groupId = group.get("id").asText();
        String aliceId = group.get("members").get(0).get("id").asText();
        String bobId = group.get("members").get(1).get("id").asText();

        mvc.perform(post("/api/groups/" + groupId + "/settlements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromMemberId":"%s","toMemberId":"%s","amount":10.00}"""
                                .formatted(bobId, aliceId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getsSuggestedSettlementsForAnUnequalThreeWaySplit() throws Exception {
        JsonNode group = createGroup("Trip", "Alice", "Bob", "Carol");
        String groupId = group.get("id").asText();
        String aliceId = group.get("members").get(0).get("id").asText();
        String bobId = group.get("members").get(1).get("id").asText();
        String carolId = group.get("members").get(2).get("id").asText();

        mvc.perform(post("/api/groups/" + groupId + "/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payerId":"%s","description":"Cab","amount":90.00}""".formatted(aliceId)))
                .andExpect(status().isCreated());

        String body = mvc.perform(get("/api/groups/" + groupId + "/settlements/suggested"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        JsonNode transfers = json.readTree(body);
        Set<String> debtors = new HashSet<>();
        for (int i = 0; i < 2; i++) {
            JsonNode transfer = transfers.get(i);
            assertThat(transfer.get("toMemberId").asText()).isEqualTo(aliceId);
            assertThat(new BigDecimal(transfer.get("amount").asText())).isEqualByComparingTo("30.00");
            debtors.add(transfer.get("fromMemberId").asText());
        }
        // Which debtor is suggested first is the minimiser's business, not the contract's.
        assertThat(debtors).containsExactlyInAnyOrder(bobId, carolId);
    }

    private JsonNode createGroup(String name, String... members) throws Exception {
        String memberList = String.join(",", java.util.Arrays.stream(members).map(m -> "\"" + m + "\"").toList());
        String created = mvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"currency\":\"INR\",\"members\":[%s]}".formatted(name, memberList)))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(created);
    }
}
