package com.aquaflow.backend.integration;

import com.aquaflow.backend.dto.request.LoginRequest;
import com.aquaflow.backend.dto.request.ReferenceMeasurementCreateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class PerformanceEvaluationIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private String adminToken;
    private String operatorToken;

    @BeforeEach
    public void setup() throws Exception {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        this.adminToken = loginAndGetToken("admin", "password123");
        this.operatorToken = loginAndGetToken("Pranz", "password123");
    }

    @Test
    @DisplayName("Verify system evaluation summary endpoint returns all 6 indicators with rigorous capabilities")
    public void shouldReturnSystemEvaluationSummaryWithAccurateCapabilities() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/evaluation/summary")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());

        // 1. Water Level
        JsonNode waterLevel = root.get("waterLevel");
        assertThat(waterLevel).isNotNull();
        assertThat(waterLevel.get("capabilityStatus").asText()).isIn("PARTIALLY_MEASURABLE", "MEASURABLE");

        // 2. Soil Moisture
        JsonNode soilMoisture = root.get("soilMoisture");
        assertThat(soilMoisture).isNotNull();
        assertThat(soilMoisture.get("capabilityStatus").asText()).isIn("NOT_MEASURABLE", "MEASURABLE");

        // 3. Activation Accuracy
        JsonNode activation = root.get("activation");
        assertThat(activation).isNotNull();
        assertThat(activation.get("capabilityStatus").asText()).isEqualTo("PARTIALLY_MEASURABLE");

        // 4. Deactivation Accuracy
        JsonNode deactivation = root.get("deactivation");
        assertThat(deactivation).isNotNull();
        assertThat(deactivation.get("capabilityStatus").asText()).isEqualTo("PARTIALLY_MEASURABLE");

        // 5. Response Time
        JsonNode responseTime = root.get("responseTime");
        assertThat(responseTime).isNotNull();
        assertThat(responseTime.get("capabilityStatus").asText()).isIn("PARTIALLY_MEASURABLE", "MEASURABLE");

        // 6. Water Consumption
        JsonNode waterConsumption = root.get("waterConsumption");
        assertThat(waterConsumption).isNotNull();
        assertThat(waterConsumption.get("capabilityStatus").asText()).isEqualTo("NOT_MEASURABLE");
        assertThat(waterConsumption.get("missingComponentExplanation").asText()).contains("flow measurement");
    }

    @Test
    @DisplayName("Verify recording and calculating reference measurements for ground-truth water level")
    public void shouldRecordWaterLevelReferenceMeasurementAndCalculateAccuracy() throws Exception {
        ReferenceMeasurementCreateRequest request = ReferenceMeasurementCreateRequest.builder()
                .indicatorType("WATER_LEVEL")
                .nodeId("NODE-01")
                .reportedValue(15.2)
                .referenceValue(15.0) // Error = 0.2, Percentage Error = (0.2/15)*100 = 1.33%, Accuracy = 98.67%
                .measurementUnit("cm")
                .instrumentDetails("Manual Still-Well Dipstick / Vernier Ruler")
                .notes("Ground-truth field calibration test")
                .measuredAt(LocalDateTime.now())
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/evaluation/reference-measurements")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("nodeId").asText()).isEqualTo("NODE-01");
        assertThat(response.get("absoluteError").asDouble()).isCloseTo(0.2, org.assertj.core.data.Offset.offset(0.001));
        assertThat(response.get("accuracyPercentage").asDouble()).isGreaterThan(98.0);

        // Fetch updated summary
        MvcResult summaryResult = mockMvc.perform(get("/api/v1/evaluation/summary?nodeId=NODE-01")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode summary = objectMapper.readTree(summaryResult.getResponse().getContentAsString());
        JsonNode wl = summary.get("waterLevel");
        assertThat(wl.get("capabilityStatus").asText()).isEqualTo("MEASURABLE");
        assertThat(wl.get("sampleCount").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Verify rejection of invalid indicator type or zero reference value handling")
    public void shouldHandleZeroReferenceWithoutDivisionByZero() throws Exception {
        ReferenceMeasurementCreateRequest zeroRefRequest = ReferenceMeasurementCreateRequest.builder()
                .indicatorType("WATER_LEVEL")
                .nodeId("NODE-01")
                .reportedValue(0.5)
                .referenceValue(0.0) // Zero reference denominator check!
                .measurementUnit("cm")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/evaluation/reference-measurements")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zeroRefRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode res = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(res.get("absoluteError").asDouble()).isEqualTo(0.5);
        // Should not crash, percentageError should be null or handled safely without NaN/infinity
        assertThat(res.get("percentageError").isNull()).isTrue();
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.get("accessToken").asText();
    }
}

