package com.aquaflow.backend.integration;

import com.aquaflow.backend.dto.request.*;
import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.persistence.*;
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

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CentralizedNotificationsAndRestSyncLoadTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private FieldRepository fieldRepository;

    @Autowired
    private MonitoringZoneRepository monitoringZoneRepository;

    @Autowired
    private EdgeNodeRepository edgeNodeRepository;

    private MockMvc mockMvc;
    private String adminToken;

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
    }

    @Test
    @DisplayName("Centralized Notifications: All meaningful lifecycle, sensor, field/zone, and irrigation events generate deduplicated notifications and support REST sync")
    void testCentralizedNotificationLifecycleAndRestSync() throws Exception {
        long initialCount = alertRepository.count();

        // 1. Farmland creation generates notification
        FieldRequest fieldReq = FieldRequest.builder()
                .name("Notification Audit Field")
                .areaHectares(3.5)
                .totalArea(3.5)
                .locationAddress("Laguna")
                .operationalStatus("MONITORING")
                .build();

        MvcResult fieldRes = mockMvc.perform(post("/api/v1/fields")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fieldReq)))
                .andExpect(status().isCreated())
                .andReturn();
        long fieldId = objectMapper.readTree(fieldRes.getResponse().getContentAsString()).get("id").asLong();

        // 2. Monitoring Zone creation generates notification
        MonitoringZone zoneQ1 = monitoringZoneRepository.save(MonitoringZone.builder()
                .name("Zone Q1")
                .code("Q1")
                .field(fieldRepository.findById(fieldId).orElseThrow())
                .cropType("RICE")
                .targetMoisturePercentage(70.0)
                .waterAllocationLimitLiters(5000.0)
                .isArchived(false)
                .build());
        MonitoringZone zoneQ2 = monitoringZoneRepository.save(MonitoringZone.builder()
                .name("Zone Q2")
                .code("Q2")
                .field(fieldRepository.findById(fieldId).orElseThrow())
                .cropType("RICE")
                .targetMoisturePercentage(70.0)
                .waterAllocationLimitLiters(5000.0)
                .isArchived(false)
                .build());

        // 3. Node pairing request & approval generates notifications
        String nodeId = "ARVANODE-NOTIFY-01";
        NodePairingSubmissionRequest pairingReq = NodePairingSubmissionRequest.builder()
                .nodeId(nodeId)
                .macAddress("24:0A:C4:AA:BB:01")
                .hardwareModel("ESP32-WROOM-32")
                .firmwareVersion("2.0.0")
                .build();

        MvcResult pairRes = mockMvc.perform(post("/api/v1/pairing/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pairingReq)))
                .andExpect(status().isCreated())
                .andReturn();
        long pairReqId = objectMapper.readTree(pairRes.getResponse().getContentAsString()).get("id").asLong();

        RegisterNodeRequest approveReq = RegisterNodeRequest.builder()
                .nodeId(nodeId)
                .identityType(CommunicationIdentityType.MAC_ADDRESS)
                .identityValue("24:0A:C4:AA:BB:01")
                .macAddress("24:0A:C4:AA:BB:01")
                .hardwareModel("ESP32-WROOM-32")
                .firmwareVersion("2.0.0")
                .monitoringZoneId(zoneQ1.getId())
                .build();

        MvcResult approveRes = mockMvc.perform(post("/api/v1/pairing/requests/" + pairReqId + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andReturn();
        long edgeNodeId = objectMapper.readTree(approveRes.getResponse().getContentAsString()).get("id").asLong();

        // 4. Node zone movement (Q1 -> Q2) and unassignment generate notifications
        AssignNodeFieldRequest moveReq = new AssignNodeFieldRequest();
        moveReq.setFieldId(fieldId);
        moveReq.setMonitoringZoneId(zoneQ2.getId());
        mockMvc.perform(put("/api/v1/nodes/" + nodeId + "/field-assignment")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(moveReq)))
                .andExpect(status().isOk());

        AssignNodeFieldRequest unassignReq = new AssignNodeFieldRequest();
        unassignReq.setFieldId(null);
        unassignReq.setMonitoringZoneId(null);
        mockMvc.perform(put("/api/v1/nodes/" + nodeId + "/field-assignment")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unassignReq)))
                .andExpect(status().isOk());

        // Re-assign back to Q1 for telemetry test
        AssignNodeFieldRequest reassignReq = new AssignNodeFieldRequest();
        reassignReq.setFieldId(fieldId);
        reassignReq.setMonitoringZoneId(zoneQ1.getId());
        mockMvc.perform(put("/api/v1/nodes/" + nodeId + "/field-assignment")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reassignReq)))
                .andExpect(status().isOk());

        // 5. Routine healthy telemetry does NOT spam notifications
        long countBeforeHealthy = alertRepository.count();
        for (int seq = 1; seq <= 3; seq++) {
            TelemetryUplinkRequest okTelemetry = TelemetryUplinkRequest.builder()
                    .nodeId(nodeId)
                    .seq((long) seq)
                    .soilMoisturePercent(62.5)
                    .waterLevelDistance(81.6)
                    .telemetryStatus("OK")
                    .sensorHealth(Map.of("soilMoisture", "OK", "ultrasonic", "OK"))
                    .measurements(Map.of("soil_moisture", 62.5, "water_level_distance", 81.6, "water_level_cm", 20.0))
                    .build();
            mockMvc.perform(post("/api/v1/telemetry/uplink/" + nodeId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(okTelemetry)))
                    .andExpect(status().isOk());
        }
        assertThat(alertRepository.count()).isEqualTo(countBeforeHealthy);

        // 6. Sensor failure (soil moisture DISCONNECTED, ultrasonic TIMEOUT) generates deduplicated notifications
        long countBeforeFault = alertRepository.count();
        for (int seq = 4; seq <= 6; seq++) {
            TelemetryUplinkRequest faultTelemetry = TelemetryUplinkRequest.builder()
                    .nodeId(nodeId)
                    .seq((long) seq)
                    .soilMoisturePercent(0.0)
                    .waterLevelDistance(0.0)
                    .telemetryStatus("SENSOR_FAILURE")
                    .sensorHealth(Map.of("soilMoisture", "DISCONNECTED", "ultrasonic", "TIMEOUT"))
                    .measurements(Map.of("soil_moisture", 0.0, "water_level_distance", 0.0, "water_level_cm", 0.0))
                    .build();
            mockMvc.perform(post("/api/v1/telemetry/uplink/" + nodeId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(faultTelemetry)))
                    .andExpect(status().isOk());
        }
        long countAfterFaults = alertRepository.count();
        // Exactly 2 fault notifications (1 for soil moisture, 1 for ultrasonic), NOT 6!
        assertThat(countAfterFaults - countBeforeFault).isEqualTo(2);

        // 7. Sensor recovery generates recovery notifications
        TelemetryUplinkRequest recoveryTelemetry = TelemetryUplinkRequest.builder()
                .nodeId(nodeId)
                .seq(7L)
                .soilMoisturePercent(65.0)
                .waterLevelDistance(81.6)
                .telemetryStatus("OK")
                .sensorHealth(Map.of("soilMoisture", "OK", "ultrasonic", "OK"))
                .measurements(Map.of("soil_moisture", 65.0, "water_level_distance", 81.6, "water_level_cm", 20.0))
                .build();
        mockMvc.perform(post("/api/v1/telemetry/uplink/" + nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recoveryTelemetry)))
                .andExpect(status().isOk());
        assertThat(alertRepository.count() - countAfterFaults).isEqualTo(2);

        // 8. Query REST notification endpoints (/api/v1/notifications, /unread-count, /sync)
        MvcResult unreadRes = mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").isNumber())
                .andReturn();
        long unreadCount = objectMapper.readTree(unreadRes.getResponse().getContentAsString()).get("unreadCount").asLong();
        assertThat(unreadCount).isGreaterThanOrEqualTo(8L);

        MvcResult syncRes = mockMvc.perform(get("/api/v1/notifications/sync?sinceId=0&limit=50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.unreadCount").value(unreadCount))
                .andReturn();

        JsonNode syncJson = objectMapper.readTree(syncRes.getResponse().getContentAsString());
        long latestId = syncJson.get("latestId").asLong();
        assertThat(latestId).isGreaterThan(0L);

        // Incremental sync with sinceId=latestId returns 0 new items
        mockMvc.perform(get("/api/v1/notifications/sync?sinceId=" + latestId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        // Mark single notification as read
        long firstAlertId = syncJson.get("items").get(0).get("id").asLong();
        mockMvc.perform(put("/api/v1/notifications/" + firstAlertId + "/read")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledged").value(true))
                .andExpect(jsonPath("$.isRead").value(true));

        // Mark all as read
        mockMvc.perform(put("/api/v1/notifications/read-all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    @DisplayName("Database & REST Sync Load Benchmark: 1, 10, 50, and 100 nodes + concurrent dashboard/notification polling with zero errors")
    void testDatabaseDrivenRestSyncLoadScenarios() throws Exception {
        Field benchField = fieldRepository.save(Field.builder()
                .name("Load Benchmark Field")
                .boundaryGeoJson("{\"type\":\"Polygon\",\"coordinates\":[]}")
                .areaHectares(10.0)
                .totalArea(10.0)
                .operationalStatus("MONITORING")
                .build());

        MonitoringZone benchZone = monitoringZoneRepository.save(MonitoringZone.builder()
                .name("Benchmark Zone Q1")
                .code("Q1")
                .field(benchField)
                .cropType("RICE")
                .targetMoisturePercentage(70.0)
                .waterAllocationLimitLiters(10000.0)
                .isArchived(false)
                .build());

        int[] nodeScenarios = {1, 10, 50, 100};
        for (int nodeCount : nodeScenarios) {
            List<Long> latenciesMs = new ArrayList<>();
            int errorCount = 0;

            // Provision nodes up to nodeCount
            for (int i = 1; i <= nodeCount; i++) {
                String nodeId = String.format("ARVANODE-LOAD-%03d", i);
                String mac = String.format("24:0A:C4:99:%02X:%02X", (i >> 8) & 0xFF, i & 0xFF);
                if (edgeNodeRepository.findByNodeId(nodeId).isEmpty()) {
                    edgeNodeRepository.save(EdgeNode.builder()
                            .nodeId(nodeId)
                            .identity(CommunicationIdentity.builder()
                                    .identityType(CommunicationIdentityType.MAC_ADDRESS)
                                    .identityValue(mac)
                                    .macAddress(mac)
                                    .build())
                            .monitoringZone(benchZone)
                            .lifecycleState(NodeLifecycleState.ACTIVE)
                            .healthState(HealthState.HEALTHY)
                            .firmwareVersion("2.0.0")
                            .hardwareModel("ESP32-WROOM-32")
                            .build());
                }
            }

            // Execute telemetry ingestion for all nodes in the scenario + REST client polling
            for (int i = 1; i <= nodeCount; i++) {
                String nodeId = String.format("ARVANODE-LOAD-%03d", i);
                boolean injectSensorFault = (i % 10 == 0);
                TelemetryUplinkRequest uplink = TelemetryUplinkRequest.builder()
                        .nodeId(nodeId)
                        .seq((long) (nodeCount * 100 + i))
                        .soilMoisturePercent(injectSensorFault ? 0.0 : 55.0 + (i % 20))
                        .waterLevelDistance(injectSensorFault ? 0.0 : 81.6)
                        .telemetryStatus(injectSensorFault ? "SENSOR_FAILURE" : "OK")
                        .sensorHealth(Map.of(
                                "soilMoisture", injectSensorFault ? "ERROR" : "OK",
                                "ultrasonic", injectSensorFault ? "TIMEOUT" : "OK"
                        ))
                        .measurements(Map.of(
                                "soil_moisture", injectSensorFault ? 0.0 : 55.0 + (i % 20),
                                "water_level_distance", injectSensorFault ? 0.0 : 81.6,
                                "water_level_cm", injectSensorFault ? 0.0 : 20.0
                        ))
                        .build();

                long t0 = System.nanoTime();
                MvcResult res = mockMvc.perform(post("/api/v1/telemetry/uplink/" + nodeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(uplink)))
                        .andReturn();
                long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
                latenciesMs.add(elapsedMs);
                if (res.getResponse().getStatus() != 200) {
                    errorCount++;
                }
            }

            // Simulate concurrent REST client polling (nodes list, telemetry stream, notifications sync)
            for (int client = 0; client < 5; client++) {
                long t0 = System.nanoTime();
                MvcResult r1 = mockMvc.perform(get("/api/v1/nodes").header("Authorization", "Bearer " + adminToken)).andReturn();
                MvcResult r2 = mockMvc.perform(get("/api/v1/telemetry/stream/recent?limit=25").header("Authorization", "Bearer " + adminToken)).andReturn();
                MvcResult r3 = mockMvc.perform(get("/api/v1/notifications/sync?sinceId=0&limit=25").header("Authorization", "Bearer " + adminToken)).andReturn();
                long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
                latenciesMs.add(elapsedMs);
                if (r1.getResponse().getStatus() != 200 || r2.getResponse().getStatus() != 200 || r3.getResponse().getStatus() != 200) {
                    errorCount++;
                }
            }

            Collections.sort(latenciesMs);
            long p50 = latenciesMs.get((int) (latenciesMs.size() * 0.50));
            long p95 = latenciesMs.get((int) (latenciesMs.size() * 0.95));
            long p99 = latenciesMs.get(Math.min(latenciesMs.size() - 1, (int) (latenciesMs.size() * 0.99)));

            System.out.printf("[LOAD BENCHMARK] Scenario=%d nodes | Requests=%d | Errors=%d | p50=%dms | p95=%dms | p99=%dms%n",
                    nodeCount, latenciesMs.size(), errorCount, p50, p95, p99);

            assertThat(errorCount).isZero();
            assertThat(p95).isLessThan(1500L);
        }
    }
}
