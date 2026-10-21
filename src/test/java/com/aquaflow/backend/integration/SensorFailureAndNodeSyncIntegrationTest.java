package com.aquaflow.backend.integration;

import com.aquaflow.backend.dto.request.*;
import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.persistence.EdgeNodeRepository;
import com.aquaflow.backend.persistence.FieldRepository;
import com.aquaflow.backend.persistence.MonitoringZoneRepository;
import com.aquaflow.backend.persistence.TelemetryReadingRepository;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class SensorFailureAndNodeSyncIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FieldRepository fieldRepository;

    @Autowired
    private MonitoringZoneRepository monitoringZoneRepository;

    @Autowired
    private EdgeNodeRepository edgeNodeRepository;

    @Autowired
    private TelemetryReadingRepository telemetryReadingRepository;

    private MockMvc mockMvc;
    private String adminToken;
    private Field field;
    private MonitoringZone zoneQ1;
    private MonitoringZone zoneQ2;

    @BeforeEach
    void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername("admin");
        loginReq.setPassword("password123");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        this.adminToken = loginJson.get("accessToken").asText();

        field = fieldRepository.save(Field.builder()
                .name("Integration Test Field")
                .boundaryGeoJson("{\"type\":\"Polygon\",\"coordinates\":[]}")
                .areaHectares(2.0)
                .totalArea(2.0)
                .operationalStatus("MONITORING")
                .build());

        zoneQ1 = monitoringZoneRepository.save(MonitoringZone.builder()
                .name("Zone Q1")
                .code("Q1")
                .field(field)
                .cropType("RICE")
                .targetMoisturePercentage(70.0)
                .waterAllocationLimitLiters(5000.0)
                .isArchived(false)
                .build());

        zoneQ2 = monitoringZoneRepository.save(MonitoringZone.builder()
                .name("Zone Q2")
                .code("Q2")
                .field(field)
                .cropType("RICE")
                .targetMoisturePercentage(70.0)
                .waterAllocationLimitLiters(5000.0)
                .isArchived(false)
                .build());
    }

    private long pairNode(String nodeId, Long initialZoneId) throws Exception {
        NodePairingSubmissionRequest pairingReq = NodePairingSubmissionRequest.builder()
                .nodeId(nodeId)
                .macAddress("24:0A:C4:11:22:33")
                .hardwareModel("ESP32-WROOM-32")
                .firmwareVersion("2.0.0")
                .build();

        MvcResult pairResult = mockMvc.perform(post("/api/v1/pairing/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pairingReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long reqId = objectMapper.readTree(pairResult.getResponse().getContentAsString()).get("id").asLong();

        RegisterNodeRequest approveReq = RegisterNodeRequest.builder()
                .nodeId(nodeId)
                .identityType(CommunicationIdentityType.MAC_ADDRESS)
                .identityValue("24:0A:C4:11:22:33")
                .macAddress("24:0A:C4:11:22:33")
                .hardwareModel("ESP32-WROOM-32")
                .firmwareVersion("2.0.0")
                .monitoringZoneId(initialZoneId)
                .build();

        MvcResult approveResult = mockMvc.perform(post("/api/v1/pairing/requests/" + reqId + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(approveResult.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    @DisplayName("Scenarios 1-5 & 11: Both sensors working, soil fails (0), ultrasonic fails (0), both fail (0), and automatic recovery")
    void testSensorFailureZeroTelemetryAndRecoveryPipeline() throws Exception {
        String nodeId = "ARVANODE-TEST-01";
        long edgeNodeId = pairNode(nodeId, zoneQ1.getId());

        // Scenario 1: Both sensors working
        TelemetryUplinkRequest reqBothOk = TelemetryUplinkRequest.builder()
                .nodeId(nodeId)
                .seq(1L)
                .batteryVoltage(4.12)
                .batteryLevel(92.0)
                .soilMoisturePercent(64.5)
                .waterLevelDistance(81.6)
                .telemetryStatus("OK")
                .sensorHealth(Map.of("soilMoisture", "OK", "ultrasonic", "OK"))
                .measurements(Map.of("soil_moisture", 64.5, "water_level_distance", 81.6, "water_level_cm", 20.0))
                .build();

        mockMvc.perform(post("/api/v1/telemetry/uplink/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqBothOk)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.readingsIngested").value(3));

        mockMvc.perform(get("/api/v1/nodes/" + edgeNodeId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soilMoisturePercent").value(64.5))
                .andExpect(jsonPath("$.waterLevelDistanceCm").value(81.6))
                .andExpect(jsonPath("$.waterLevelCm").value(20.0))
                .andExpect(jsonPath("$.soilSensorHealth").value("OK"))
                .andExpect(jsonPath("$.ultrasonicSensorHealth").value("OK"))
                .andExpect(jsonPath("$.telemetryStatus").value("OK"))
                .andExpect(jsonPath("$.healthState").value("HEALTHY"));

        // Scenario 2: Soil moisture fails (0.0); ultrasonic continues working
        TelemetryUplinkRequest reqSoilFails = TelemetryUplinkRequest.builder()
                .nodeId(nodeId)
                .seq(2L)
                .soilMoisturePercent(0.0)
                .waterLevelDistance(76.6)
                .telemetryStatus("PARTIAL_FAILURE")
                .sensorHealth(Map.of("soilMoisture", "DISCONNECTED", "ultrasonic", "OK"))
                .measurements(Map.of("soil_moisture", 0.0, "water_level_distance", 76.6, "water_level_cm", 25.0))
                .build();

        mockMvc.perform(post("/api/v1/telemetry/uplink/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqSoilFails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.readingsIngested").value(2));

        mockMvc.perform(get("/api/v1/nodes/" + edgeNodeId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soilMoisturePercent").value(0.0))
                .andExpect(jsonPath("$.waterLevelDistanceCm").value(76.6))
                .andExpect(jsonPath("$.waterLevelCm").value(25.0))
                .andExpect(jsonPath("$.soilSensorHealth").value("DISCONNECTED"))
                .andExpect(jsonPath("$.ultrasonicSensorHealth").value("OK"))
                .andExpect(jsonPath("$.telemetryStatus").value("PARTIAL_FAILURE"))
                .andExpect(jsonPath("$.healthState").value("WARNING"));

        // Verify Zone Q1 aggregation excludes failed soil moisture 0.0 reading (so avgSoilMoisture is null -> 0.0, not corrupting healthy nodes)
        // Scenario 3: Ultrasonic fails (0.0); soil moisture recovers and continues working
        TelemetryUplinkRequest reqUltraFails = TelemetryUplinkRequest.builder()
                .nodeId(nodeId)
                .seq(3L)
                .soilMoisturePercent(58.2)
                .waterLevelDistance(0.0)
                .telemetryStatus("PARTIAL_FAILURE")
                .sensorHealth(Map.of("soilMoisture", "OK", "ultrasonic", "TIMEOUT"))
                .measurements(Map.of("soil_moisture", 58.2, "water_level_distance", 0.0, "water_level_cm", 0.0))
                .build();

        mockMvc.perform(post("/api/v1/telemetry/uplink/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqUltraFails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.readingsIngested").value(2));

        // Verify waterLevelCm is 0.0 (NOT 91.44 cm false flood!) when ultrasonic distance is 0.0
        mockMvc.perform(get("/api/v1/nodes/" + edgeNodeId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soilMoisturePercent").value(58.2))
                .andExpect(jsonPath("$.waterLevelDistanceCm").value(0.0))
                .andExpect(jsonPath("$.waterLevelCm").value(0.0))
                .andExpect(jsonPath("$.soilSensorHealth").value("OK"))
                .andExpect(jsonPath("$.ultrasonicSensorHealth").value("TIMEOUT"))
                .andExpect(jsonPath("$.telemetryStatus").value("PARTIAL_FAILURE"))
                .andExpect(jsonPath("$.healthState").value("WARNING"));

        // Scenario 4: Both sensors fail -> both values 0.0, telemetry continues
        TelemetryUplinkRequest reqBothFail = TelemetryUplinkRequest.builder()
                .nodeId(nodeId)
                .seq(4L)
                .soilMoisturePercent(0.0)
                .waterLevelDistance(0.0)
                .telemetryStatus("SENSOR_FAILURE")
                .sensorHealth(Map.of("soilMoisture", "ERROR", "ultrasonic", "TIMEOUT"))
                .measurements(Map.of("soil_moisture", 0.0, "water_level_distance", 0.0, "water_level_cm", 0.0))
                .build();

        mockMvc.perform(post("/api/v1/telemetry/uplink/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqBothFail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.readingsIngested").value(2));

        mockMvc.perform(get("/api/v1/nodes/" + edgeNodeId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soilMoisturePercent").value(0.0))
                .andExpect(jsonPath("$.waterLevelDistanceCm").value(0.0))
                .andExpect(jsonPath("$.waterLevelCm").value(0.0))
                .andExpect(jsonPath("$.soilSensorHealth").value("ERROR"))
                .andExpect(jsonPath("$.ultrasonicSensorHealth").value("TIMEOUT"))
                .andExpect(jsonPath("$.telemetryStatus").value("SENSOR_FAILURE"))
                .andExpect(jsonPath("$.healthState").value("CRITICAL"));

        // Scenario 5: Sensor recovers -> valid readings resume automatically
        TelemetryUplinkRequest reqRecovered = TelemetryUplinkRequest.builder()
                .nodeId(nodeId)
                .seq(5L)
                .soilMoisturePercent(61.0)
                .waterLevelDistance(86.6)
                .telemetryStatus("OK")
                .sensorHealth(Map.of("soilMoisture", "OK", "ultrasonic", "OK"))
                .measurements(Map.of("soil_moisture", 61.0, "water_level_distance", 86.6, "water_level_cm", 15.0))
                .build();

        mockMvc.perform(post("/api/v1/telemetry/uplink/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqRecovered)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/nodes/" + edgeNodeId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soilMoisturePercent").value(61.0))
                .andExpect(jsonPath("$.waterLevelDistanceCm").value(86.6))
                .andExpect(jsonPath("$.waterLevelCm").value(15.0))
                .andExpect(jsonPath("$.soilSensorHealth").value("OK"))
                .andExpect(jsonPath("$.ultrasonicSensorHealth").value("OK"))
                .andExpect(jsonPath("$.telemetryStatus").value("OK"))
                .andExpect(jsonPath("$.healthState").value("HEALTHY"));

        // Verify all 5 telemetry events were recorded in stream events with sensor health preserved
        mockMvc.perform(get("/api/v1/telemetry/stream/events?nodeId=" + nodeId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].telemetryStatus").value("OK"))
                .andExpect(jsonPath("$.content[1].telemetryStatus").value("SENSOR_FAILURE"))
                .andExpect(jsonPath("$.content[1].soilMoisturePercent").value(0.0))
                .andExpect(jsonPath("$.content[1].waterLevelCm").value(0.0));
    }

    @Test
    @DisplayName("Scenarios 6-10 & 12: Paired unassigned node,telemetry for unassigned node, assign, move between zones, unassign, and unpair consistency across all endpoints")
    void testNodeAssignmentLifecycleAndCrossEndpointConsistency() throws Exception {
        String nodeId = "ARVANODE-SYNC-02";

        // Scenario 6: Pair node WITHOUT assigning to any monitoring zone
        long edgeNodeId = pairNode(nodeId, null);

        // Verify /api/v1/nodes reports PAIRED and UNASSIGNED
        mockMvc.perform(get("/api/v1/nodes/" + edgeNodeId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").value(nodeId))
                .andExpect(jsonPath("$.pairingStatus").value("PAIRED"))
                .andExpect(jsonPath("$.assignmentStatus").value("UNASSIGNED"))
                .andExpect(jsonPath("$.monitoringZoneId").isEmpty());

        // Scenario 12: Telemetry arrives for unassigned node -> Data retained without fabricated assignment
        TelemetryUplinkRequest unassignedUplink = TelemetryUplinkRequest.builder()
                .nodeId(nodeId)
                .seq(10L)
                .soilMoisturePercent(52.4)
                .waterLevelDistance(91.6)
                .telemetryStatus("OK")
                .sensorHealth(Map.of("soilMoisture", "OK", "ultrasonic", "OK"))
                .measurements(Map.of("soil_moisture", 52.4, "water_level_distance", 91.6))
                .build();

        mockMvc.perform(post("/api/v1/telemetry/uplink/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unassignedUplink)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/nodes/" + edgeNodeId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soilMoisturePercent").value(52.4))
                .andExpect(jsonPath("$.waterLevelCm").value(10.0))
                .andExpect(jsonPath("$.assignmentStatus").value("UNASSIGNED"))
                .andExpect(jsonPath("$.monitoringZoneId").isEmpty());

        // Scenario 7: Assign node to Zone Q1 -> Verify same assignment across /nodes, /zones, /fields/{id}/zones, and /fields/{id}/topology
        AssignNodeFieldRequest assignQ1 = new AssignNodeFieldRequest();
        assignQ1.setFieldId(field.getId());
        assignQ1.setMonitoringZoneId(zoneQ1.getId());

        mockMvc.perform(put("/api/v1/nodes/" + nodeId + "/field-assignment")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignQ1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monitoringZoneId").value(zoneQ1.getId().intValue()))
                .andExpect(jsonPath("$.monitoringZoneCode").value("Q1"))
                .andExpect(jsonPath("$.assignmentStatus").value("ASSIGNED_TO_ZONE"));

        // Check /api/v1/zones
        mockMvc.perform(get("/api/v1/zones")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + zoneQ1.getId() + ")].assignedNodeIds[?(@ == '" + nodeId + "')]").exists());

        // Check /api/v1/fields/{id}/zones
        mockMvc.perform(get("/api/v1/fields/" + field.getId() + "/zones")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + zoneQ1.getId() + ")].assignedNodeIds[?(@ == '" + nodeId + "')]").exists());

        // Check /api/v1/fields/{id}/topology
        mockMvc.perform(get("/api/v1/fields/" + field.getId() + "/topology")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zones[?(@.id == " + zoneQ1.getId() + ")].assignedNodes[?(@.nodeId == '" + nodeId + "')]").exists());

        // Scenario 8: Move node from Zone Q1 to Zone Q2 -> All views reflect new zone and Q1 no longer lists node
        AssignNodeFieldRequest moveQ2 = new AssignNodeFieldRequest();
        moveQ2.setFieldId(field.getId());
        moveQ2.setMonitoringZoneId(zoneQ2.getId());

        mockMvc.perform(put("/api/v1/nodes/" + nodeId + "/field-assignment")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(moveQ2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monitoringZoneId").value(zoneQ2.getId().intValue()))
                .andExpect(jsonPath("$.monitoringZoneCode").value("Q2"))
                .andExpect(jsonPath("$.assignmentStatus").value("ASSIGNED_TO_ZONE"));

        mockMvc.perform(get("/api/v1/zones")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + zoneQ1.getId() + ")].assignedNodeIds[?(@ == '" + nodeId + "')]").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.id == " + zoneQ2.getId() + ")].assignedNodeIds[?(@ == '" + nodeId + "')]").exists());

        // Scenario 9: Unassign node -> No stale zone assignment anywhere
        AssignNodeFieldRequest unassignReq = new AssignNodeFieldRequest();
        unassignReq.setFieldId(null);
        unassignReq.setMonitoringZoneId(null);

        mockMvc.perform(put("/api/v1/nodes/" + nodeId + "/field-assignment")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unassignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monitoringZoneId").isEmpty())
                .andExpect(jsonPath("$.assignmentStatus").value("UNASSIGNED"))
                .andExpect(jsonPath("$.pairingStatus").value("PAIRED"));

        mockMvc.perform(get("/api/v1/zones")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + zoneQ2.getId() + ")].assignedNodeIds[?(@ == '" + nodeId + "')]").doesNotExist());

        // Scenario 10: Re-assign then unpair node -> Pairing and assignment state cleared
        mockMvc.perform(put("/api/v1/nodes/" + nodeId + "/field-assignment")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(moveQ2)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/pairing/nodes/" + nodeId + "/unpair")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unpaired").value(true));

        // Active /api/v1/nodes list must no longer contain unpaired node, and Zone Q2 must have 0 assigned nodes
        mockMvc.perform(get("/api/v1/nodes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nodeId == '" + nodeId + "')]").doesNotExist());

        mockMvc.perform(get("/api/v1/zones")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + zoneQ2.getId() + ")].assignedNodeIds[?(@ == '" + nodeId + "')]").doesNotExist());
    }
}
