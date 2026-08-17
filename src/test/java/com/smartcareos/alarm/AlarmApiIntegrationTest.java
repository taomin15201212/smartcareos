package com.smartcareos.alarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AlarmApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void createsAnAlarmIdempotentlyAndCompletesTheWorkflow() throws Exception {
        String createRequest = """
                {
                  "tenantId": "institution-api-test",
                  "elderId": "elder-100",
                  "sourceEventId": "device-event-api-100",
                  "severity": "HIGH"
                }
                """;

        String response = mockMvc.perform(post("/api/v1/alarms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(response);
        String alarmId = created.get("id").asText();

        mockMvc.perform(post("/api/v1/alarms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alarmId));

        performAction(alarmId, "acknowledge", "staff-1", "ACKNOWLEDGED");
        performAction(alarmId, "start", "staff-1", "IN_PROGRESS");
        performAction(alarmId, "resolve", "staff-1", "RESOLVED");
        performAction(alarmId, "close", "supervisor-1", "CLOSED");

        String finalState = mockMvc.perform(get("/api/v1/alarms/{id}", alarmId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(finalState).get("transitions").size()).isEqualTo(5);
    }

    @Test
    void rejectsInvalidTransition() throws Exception {
        String createRequest = """
                {
                  "tenantId": "institution-invalid-test",
                  "elderId": "elder-200",
                  "sourceEventId": "device-event-invalid-200",
                  "severity": "MEDIUM"
                }
                """;
        String response = mockMvc.perform(post("/api/v1/alarms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String alarmId = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(post("/api/v1/alarms/{id}/close", alarmId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actorId\":\"staff-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALARM_STATE_CONFLICT"));
    }

    private void performAction(String alarmId, String action, String actorId, String expectedStatus)
            throws Exception {
        mockMvc.perform(post("/api/v1/alarms/{id}/{action}", alarmId, action)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actorId\":\"" + actorId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }
}
