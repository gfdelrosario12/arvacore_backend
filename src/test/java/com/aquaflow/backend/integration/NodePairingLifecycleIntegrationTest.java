package com.aquaflow.backend.integration;

import com.aquaflow.backend.dto.request.LoginRequest;
import com.aquaflow.backend.dto.request.NodePairingSubmissionRequest;
import com.aquaflow.backend.dto.request.RegisterNodeRequest;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class NodePairingLifecycleIntegrationTest {

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
    @DisplayName("Pairing lifecycle: pending requests expire after 30 minutes and reject approval")
    @Transactional
    public void shouldEnforce30MinuteExpirationForPendingPairing() throws Exception {
        String adminToken = getAuthToken("admin", "password123");

        // Create an expired pairing request (> 30 minutes old)
        LocalDateTime requestedAt = LocalDateTime.now().minusMinutes(35);
        NodePairingRequest expiredReq = NodePairingRequest.builder()
                .nodeId("NODE-EXP-30M")
                .macAddress("11:22:33:44:55:66")
                .hardwareModel("ESP32-WROOM-32")
                .firmwareVersion("2.1.0")
                .pairingStatus(PairingStatus.PENDING)
                .requestedAt(requestedAt)
                .expiresAt(requestedAt.plusMinutes(30))
                .build();
        expiredReq = pairingRequestRepository.save(expiredReq);

        // Verification: checking status returns EXPIRED
        MvcResult statusResult = mockMvc.perform(get("/api/v1/pairing/status/NODE-EXP-30M"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode statusJson = objectMapper.readTree(statusResult.getResponse().getContentAsString());
        assertThat(statusJson.get("pairingStatus").asText()).isEqualTo("EXPIRED");
        assertThat(statusJson.get("expired").asBoolean()).isTrue();

        // Attempt approval: must be rejected with 400 ValidationException
        RegisterNodeRequest registerReq = RegisterNodeRequest.builder()
                .nodeId("NODE-EXP-30M")
                .build();

        mockMvc.perform(post("/api/v1/pairing/requests/" + expiredReq.getId() + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Accepted paired nodes remain valid beyond 30 minutes")
    @Transactional
    public void shouldKeepAcceptedPairingValidIndefinitely() throws Exception {
        // Create an approved node from 10 days ago
        EdgeNode node = EdgeNode.builder()
                .nodeId("NODE-PERM-OK")
                .identity(CommunicationIdentity.builder()
                        .identityType(CommunicationIdentityType.MAC_ADDRESS)
                        .identityValue("11:22:33:44:55:66")
                        .macAddress("11:22:33:44:55:66")
                        .build())
                .lifecycleState(NodeLifecycleState.ACTIVE)
                .healthState(com.aquaflow.backend.entity.HealthState.HEALTHY)
                .build();
        edgeNodeRepository.save(node);

        LocalDateTime requestedAt = LocalDateTime.now().minusDays(10);
        NodePairingRequest approvedReq = NodePairingRequest.builder()
                .nodeId("NODE-PERM-OK")
                .pairingStatus(PairingStatus.APPROVED)
                .requestedAt(requestedAt)
                .expiresAt(requestedAt.plusMinutes(30))
                .processedAt(requestedAt.plusMinutes(2))
                .processedBy("ADMIN")
                .build();
        pairingRequestRepository.save(approvedReq);

        // Checking status returns APPROVED, not expired
        MvcResult statusResult = mockMvc.perform(get("/api/v1/pairing/status/NODE-PERM-OK"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode statusJson = objectMapper.readTree(statusResult.getResponse().getContentAsString());
        assertThat(statusJson.get("pairingStatus").asText()).isEqualTo("APPROVED");
        assertThat(statusJson.get("expired").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Node unpairing: authorized users can unpair, unauthorized users cannot")
    @Transactional
    public void shouldEnforceRbacOnNodeUnpairing() throws Exception {
        String adminToken = getAuthToken("admin", "password123");
        String operatorToken = getAuthToken("Pranz", "password123");
        String supervisorToken = getAuthToken("Jenice", "password123");
        String userToken = getAuthToken("MrFarmer", "password123");

        // Setup active node
        EdgeNode node = EdgeNode.builder()
                .nodeId("NODE-RBAC-UNPAIR")
                .identity(CommunicationIdentity.builder()
                        .identityType(CommunicationIdentityType.MAC_ADDRESS)
                        .identityValue("22:33:44:55:66:77")
                        .macAddress("22:33:44:55:66:77")
                        .build())
                .lifecycleState(NodeLifecycleState.ACTIVE)
                .healthState(com.aquaflow.backend.entity.HealthState.HEALTHY)
                .build();
        edgeNodeRepository.save(node);

        // 1. Unauthenticated request -> 401 Unauthorized
        mockMvc.perform(delete("/api/v1/nodes/NODE-RBAC-UNPAIR/pairing"))
                .andExpect(status().isUnauthorized());

        // 2. Supervisor request -> 403 Forbidden
        mockMvc.perform(delete("/api/v1/nodes/NODE-RBAC-UNPAIR/pairing")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isForbidden());

        // 3. User request -> 403 Forbidden
        mockMvc.perform(delete("/api/v1/nodes/NODE-RBAC-UNPAIR/pairing")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // 4. Operator request -> 200 OK
        MvcResult opResult = mockMvc.perform(delete("/api/v1/nodes/NODE-RBAC-UNPAIR/pairing")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode opJson = objectMapper.readTree(opResult.getResponse().getContentAsString());
        assertThat(opJson.get("unpaired").asBoolean()).isTrue();
        assertThat(opJson.get("nodeId").asText()).isEqualTo("NODE-RBAC-UNPAIR");

        // 5. Admin request on already unpaired node -> 200 OK (safely repeatable)
        MvcResult adminResult = mockMvc.perform(delete("/api/v1/nodes/NODE-RBAC-UNPAIR/pairing")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode adminJson = objectMapper.readTree(adminResult.getResponse().getContentAsString());
        assertThat(adminJson.get("unpaired").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Revoked node cannot submit telemetry, discovery returns REVOKED")
    @Transactional
    public void shouldRejectTelemetryFromRevokedNode() throws Exception {
        String adminToken = getAuthToken("admin", "password123");

        // Setup active node
        EdgeNode node = EdgeNode.builder()
                .nodeId("NODE-REVOKED-TLM")
                .identity(CommunicationIdentity.builder()
                        .identityType(CommunicationIdentityType.MAC_ADDRESS)
                        .identityValue("33:44:55:66:77:88")
                        .macAddress("33:44:55:66:77:88")
                        .build())
                .lifecycleState(NodeLifecycleState.ACTIVE)
                .healthState(com.aquaflow.backend.entity.HealthState.HEALTHY)
                .build();
        edgeNodeRepository.save(node);

        // Unpair the node
        mockMvc.perform(delete("/api/v1/nodes/NODE-REVOKED-TLM/pairing")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Status check returns REVOKED
        MvcResult statusResult = mockMvc.perform(get("/api/v1/pairing/status/NODE-REVOKED-TLM"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode statusJson = objectMapper.readTree(statusResult.getResponse().getContentAsString());
        assertThat(statusJson.get("pairingStatus").asText()).isEqualTo("REVOKED");

        // Telemetry uplink must be rejected
        TelemetryUplinkRequest uplink = TelemetryUplinkRequest.builder()
                .nodeId("NODE-REVOKED-TLM")
                .seq(1L)
                .soilMoisturePercent(45.0)
                .waterLevelDistance(15.0)
                .measurements(Map.of("soil_moisture", 45.0))
                .build();

        mockMvc.perform(post("/api/v1/telemetry/uplink/NODE-REVOKED-TLM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uplink)))
                .andExpect(status().isBadRequest());
    }
}
