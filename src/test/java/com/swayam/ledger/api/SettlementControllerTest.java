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

    private JsonNode createGroup(String name, String... members) throws Exception {
        String memberList = String.join(",", java.util.Arrays.stream(members).map(m -> "\"" + m + "\"").toList());
        String created = mvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"currency\":\"INR\",\"members\":[%s]}".formatted(name, memberList)))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(created);
    }
}
