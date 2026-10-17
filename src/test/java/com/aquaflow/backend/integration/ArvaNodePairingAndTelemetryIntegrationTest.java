package com.aquaflow.backend.integration;

import com.aquaflow.backend.dto.request.LoginRequest;
import com.aquaflow.backend.dto.request.NodePairingSubmissionRequest;
import com.aquaflow.backend.dto.request.RegisterNodeRequest;
import com.aquaflow.backend.dto.request.TelemetryUplinkRequest;
import com.aquaflow.backend.entity.CommunicationIdentityType;
import com.aquaflow.backend.entity.Field;
import com.aquaflow.backend.entity.MonitoringZone;
import com.aquaflow.backend.persistence.FieldRepository;
import com.aquaflow.backend.persistence.MonitoringZoneRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ArvaNodePairingAndTelemetryIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FieldRepository fieldRepository;

    @Autowired
    private MonitoringZoneRepository monitoringZoneRepository;

    private MockMvc mockMvc;
    private String adminToken;
    private MonitoringZone zone1;

    @BeforeEach
    void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        // Login as admin
        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername("admin");
        loginReq.setPassword("admin_pass");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        this.adminToken = loginJson.get("accessToken").asText();

        // Ensure at least 1 field and zone exist
        Field field = fieldRepository.findAll().stream().findFirst().orElseGet(() ->
                fieldRepository.save(Field.builder()
                        .name("Field 1")
                        .boundaryGeoJson("{\"type\":\"Polygon\",\"coordinates\":[]}")
                        .areaHectares(5.0)
                        .build()));

        zone1 = monitoringZoneRepository.findAll().stream().findFirst().orElseGet(() ->
                monitoringZoneRepository.save(MonitoringZone.builder()
                        .name("Zone 1")
                        .field(field)
                        .cropType("RICE")
                        .targetMoisturePercentage(75.0)
                        .waterAllocationLimitLiters(5000.0)
                        .build()));
    }

    @Test
    @DisplayName("End-to-end: ESP32 unauthenticated pairing, admin approval, 3-second telemetry streaming, stream events, and topology visibility")
    void testEndToEndPairingAndTelemetryFlow() throws Exception {
        String nodeId = "NODE-C3E8";

        // 1. ESP32 sends pairing request WITHOUT authorization header -> Must return HTTP 201
        NodePairingSubmissionRequest pairingReq = NodePairingSubmissionRequest.builder()
                .nodeId(nodeId)
                .macAddress("24:0A:C4:00:C3:E8")
                .hardwareModel("ESP32-WROOM-32")
                .firmwareVersion("1.0.0")
                .build();

        MvcResult pairResult = mockMvc.perform(post("/api/v1/pairing/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pairingReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nodeId").value(nodeId))
                .andExpect(jsonPath("$.pairingStatus").value("PENDING"))
                .andReturn();

        JsonNode pairJson = objectMapper.readTree(pairResult.getResponse().getContentAsString());
        long pairingRequestId = pairJson.get("id").asLong();
        assertThat(pairingRequestId).isGreaterThan(0L);

        // 2. ESP32 polls pairing status WITHOUT authorization header -> Must return HTTP 200 with PENDING
        mockMvc.perform(get("/api/v1/pairing/status/" + nodeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").value(nodeId))
                .andExpect(jsonPath("$.pairingStatus").value("PENDING"))
                .andExpect(jsonPath("$.recognized").value(false));

        // 3. Admin lists pending pairing requests -> Must contain NODE-C3E8
        mockMvc.perform(get("/api/v1/pairing/requests/pending")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nodeId == '" + nodeId + "')]").exists());

        // 4. Admin approves pairing request and assigns to Zone 1 -> Must return HTTP 200
        RegisterNodeRequest approveReq = RegisterNodeRequest.builder()
                .nodeId(nodeId)
                .identityType(CommunicationIdentityType.MAC_ADDRESS)
                .identityValue("24:0A:C4:00:C3:E8")
                .macAddress("24:0A:C4:00:C3:E8")
                .hardwareModel("ESP32-WROOM-32")
                .firmwareVersion("1.0.0")
                .monitoringZoneId(zone1.getId())
                .build();

        mockMvc.perform(post("/api/v1/pairing/requests/" + pairingRequestId + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").value(nodeId));

        // 5. ESP32 polls pairing status again WITHOUT authorization header -> Must return APPROVED and recognized: true
        mockMvc.perform(get("/api/v1/pairing/status/" + nodeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").value(nodeId))
                .andExpect(jsonPath("$.pairingStatus").value("APPROVED"))
                .andExpect(jsonPath("$.recognized").value(true));

        // 6. ESP32 transmits telemetry seq 7 WITHOUT authorization header -> Must return HTTP 200
        TelemetryUplinkRequest uplinkSeq7 = TelemetryUplinkRequest.builder()
                .nodeId(nodeId)
                .seq(7L)
                .batteryVoltage(4.15)
                .batteryLevel(95.0)
                .soilMoisturePercent(45.59)
                .measurements(Map.of(
                        "soil_moisture", 45.59,
                        "water_level_distance", 123.15,
                        "waterLevelCm", -103.15
                ))
                .build();

        mockMvc.perform(post("/api/v1/telemetry/uplink/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uplinkSeq7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.nodeId").value(nodeId));

        // 7. ESP32 transmits subsequent 3-second telemetry bursts (seq 8 and seq 9)
        TelemetryUplinkRequest uplinkSeq8 = TelemetryUplinkRequest.builder()
                .nodeId(nodeId)
                .seq(8L)
                .batteryVoltage(4.15)
                .batteryLevel(95.0)
                .soilMoisturePercent(45.62)
                .measurements(Map.of(
                        "soil_moisture", 45.62,
                        "water_level_distance", 123.10
                ))
                .build();

        mockMvc.perform(post("/api/v1/telemetry/uplink/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uplinkSeq8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        TelemetryUplinkRequest uplinkSeq9 = TelemetryUplinkRequest.builder()
                .nodeId(nodeId)
                .seq(9L)
                .batteryVoltage(4.14)
                .batteryLevel(94.0)
                .soilMoisturePercent(45.60)
                .measurements(Map.of(
                        "soil_moisture", 45.60,
                        "water_level_distance", 123.12
                ))
                .build();

        mockMvc.perform(post("/api/v1/telemetry/uplink/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uplinkSeq9)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 8. Admin queries Telemetry Stream Events -> Must contain all 3 events with PROCESSED status
        mockMvc.perform(get("/api/v1/telemetry/stream/events?nodeId=" + nodeId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].nodeId").value(nodeId))
                .andExpect(jsonPath("$.content[0].status").value("PROCESSED"));

        // 9. Admin queries Telemetry Stream Summary -> Must report totalTransmissions >= 3
        mockMvc.perform(get("/api/v1/telemetry/stream/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransmissions").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));

        // 10. Admin queries Nodes endpoint -> NODE-C3E8 is returned with latest telemetry
        mockMvc.perform(get("/api/v1/nodes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nodeId == '" + nodeId + "')]").exists())
                .andExpect(jsonPath("$[?(@.nodeId == '" + nodeId + "')].monitoringZoneId").value(zone1.getId().intValue()));

        // 11. Admin queries Field topology -> Zone 1 contains NODE-C3E8
        mockMvc.perform(get("/api/v1/fields/" + zone1.getField().getId() + "/topology")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zones[?(@.id == " + zone1.getId() + ")].assignedNodes[?(@.nodeId == '" + nodeId + "')]").exists());
    }
}
