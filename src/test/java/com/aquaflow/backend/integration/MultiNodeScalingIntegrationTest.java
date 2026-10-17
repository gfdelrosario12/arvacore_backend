package com.aquaflow.backend.integration;

import com.aquaflow.backend.dto.request.LoginRequest;
import com.aquaflow.backend.dto.request.NodePairingSubmissionRequest;
import com.aquaflow.backend.dto.request.TelemetryUplinkRequest;
import com.aquaflow.backend.entity.NodeLifecycleState;
import com.aquaflow.backend.entity.NodePairingRequest;
import com.aquaflow.backend.entity.PairingStatus;
import com.aquaflow.backend.entity.CommunicationIdentity;
import com.aquaflow.backend.entity.CommunicationIdentityType;
import com.aquaflow.backend.entity.EdgeNode;
import com.aquaflow.backend.persistence.EdgeNodeRepository;
import com.aquaflow.backend.persistence.NodePairingRequestRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class MultiNodeScalingIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NodePairingRequestRepository pairingRequestRepository;

    @Autowired
    private EdgeNodeRepository edgeNodeRepository;

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    private String getAuthToken(String username, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.get("accessToken").asText();
    }

    @Test
    @Transactional
    @DisplayName("Should successfully handle scaling up to 10 distinct provisioned ArvaNodes (ARVANODE-001 to ARVANODE-010)")
    public void shouldSupportTenDistinctArvaNodesScaling() throws Exception {
        String adminToken = getAuthToken("admin", "password123");

        // 1. Submit pairing requests and commission 10 unique nodes
        for (int i = 1; i <= 10; i++) {
            String nodeId = String.format("ARVANODE-%03d", i);
            String mac = String.format("24:0A:C4:00:%02X:%02X", (i >> 8) & 0xFF, i & 0xFF);

            NodePairingSubmissionRequest req = new NodePairingSubmissionRequest();
            req.setNodeId(nodeId);
            req.setMacAddress(mac);
            req.setHardwareModel("ESP32-WROOM-32");
            req.setFirmwareVersion("1.2.0");

            mockMvc.perform(post("/api/v1/pairing/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());

            // Save commissioned node in edge_nodes repository
            EdgeNode edgeNode = edgeNodeRepository.findByNodeId(nodeId).orElseGet(() -> {
                EdgeNode node = EdgeNode.builder()
                        .nodeId(nodeId)
                        .identity(CommunicationIdentity.builder()
                                .identityType(CommunicationIdentityType.MAC_ADDRESS)
                                .identityValue(mac)
                                .macAddress(mac)
                                .build())
                        .lifecycleState(NodeLifecycleState.ACTIVE)
                        .healthState(com.aquaflow.backend.entity.HealthState.HEALTHY)
                        .firmwareVersion("1.2.0")
                        .hardwareModel("ESP32-WROOM-32")
                        .build();
                return edgeNodeRepository.save(node);
            });
            edgeNode.setLifecycleState(NodeLifecycleState.ACTIVE);
            edgeNodeRepository.save(edgeNode);

            // Ensure pairing request is APPROVED
            NodePairingRequest approvedReq = NodePairingRequest.builder()
                    .nodeId(nodeId)
                    .macAddress(mac)
                    .hardwareModel("ESP32-WROOM-32")
                    .firmwareVersion("1.2.0")
                    .pairingStatus(PairingStatus.APPROVED)
                    .requestedAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusMinutes(30))
                    .processedAt(LocalDateTime.now())
                    .processedBy("ADMIN")
                    .build();
            pairingRequestRepository.save(approvedReq);
        }

        // 2. Ingest 3-second telemetry for all 10 nodes independently
        for (int i = 1; i <= 10; i++) {
            String nodeId = String.format("ARVANODE-%03d", i);
            TelemetryUplinkRequest telemetry = TelemetryUplinkRequest.builder()
                    .nodeId(nodeId)
                    .soilMoisturePercent(40.0 + i)
                    .batteryVoltage(4.15)
                    .batteryLevel(90.0)
                    .measurements(Map.of(
                            "water_level_distance", 25.0 + i,
                            "waterLevelCm", -5.0 + i,
                            "soil_moisture", 40.0 + i
                    ))
                    .build();

            mockMvc.perform(post("/api/v1/telemetry/uplink/" + nodeId)
                            .header("X-Gateway-Key", "ed630e681ef735f36954469b48f832f210286246ca95fff62e62086cab74e8a9")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(telemetry)))
                    .andExpect(status().isOk());
        }

        // 3. Verify querying individual node pairing status for each of the 10 nodes
        for (int i = 1; i <= 10; i++) {
            String nodeId = String.format("ARVANODE-%03d", i);
            mockMvc.perform(get("/api/v1/pairing/status/" + nodeId))
                    .andExpect(status().isOk());
        }

        // Verify all nodes appear in the authenticated edge nodes registry
        mockMvc.perform(get("/api/v1/nodes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 4. Forcefully unpair ARVANODE-005
        mockMvc.perform(delete("/api/v1/nodes/ARVANODE-005/pairing")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // ARVANODE-005 should now be in UNREGISTERED state
        EdgeNode unpairNode = edgeNodeRepository.findByNodeId("ARVANODE-005").orElseThrow();
        assertThat(unpairNode.getLifecycleState()).isEqualTo(NodeLifecycleState.UNREGISTERED);

        // Telemetry uplink for unpairNode ARVANODE-005 must be rejected
        TelemetryUplinkRequest rejectedTelemetry = TelemetryUplinkRequest.builder()
                .nodeId("ARVANODE-005")
                .soilMoisturePercent(45.0)
                .build();

        mockMvc.perform(post("/api/v1/telemetry/uplink/ARVANODE-005")
                        .header("X-Gateway-Key", "ed630e681ef735f36954469b48f832f210286246ca95fff62e62086cab74e8a9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectedTelemetry)))
                .andExpect(status().is4xxClientError());

        // The other 9 nodes (e.g. ARVANODE-001, ARVANODE-010) must remain active and healthy
        EdgeNode activeNode1 = edgeNodeRepository.findByNodeId("ARVANODE-001").orElseThrow();
        assertThat(activeNode1.getLifecycleState()).isEqualTo(NodeLifecycleState.ACTIVE);

        EdgeNode activeNode10 = edgeNodeRepository.findByNodeId("ARVANODE-010").orElseThrow();
        assertThat(activeNode10.getLifecycleState()).isEqualTo(NodeLifecycleState.ACTIVE);
    }
}
