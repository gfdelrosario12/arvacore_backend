package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.NodePairingSubmissionRequest;
import com.aquaflow.backend.dto.request.RegisterNodeRequest;
import com.aquaflow.backend.dto.response.EdgeNodeResponse;
import com.aquaflow.backend.dto.response.PairingRequestResponse;
import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.infrastructure.event.SystemEventPublisher;
import com.aquaflow.backend.persistence.SystemAuditLogRepository;
import com.aquaflow.backend.persistence.EdgeNodeRepository;
import com.aquaflow.backend.persistence.NodePairingRequestRepository;
import com.aquaflow.backend.persistence.SystemStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class NodePairingServiceImplTest {

    @Mock
    private NodePairingRequestRepository pairingRequestRepository;

    @Mock
    private EdgeNodeRepository edgeNodeRepository;

    @Mock
    private EdgeNodeRegistryService edgeNodeRegistryService;

    @Mock
    private SystemStateRepository systemStateRepository;

    @Mock
    private SystemAuditLogRepository auditLogRepository;

    @Mock
    private SystemEventPublisher systemEventPublisher;

    @Mock
    private com.aquaflow.backend.persistence.DeviceCredentialRepository deviceCredentialRepository;

    @InjectMocks
    private NodePairingServiceImpl nodePairingService;

    private SystemState activeSystemState;

    @BeforeEach
    void setUp() {
        activeSystemState = SystemState.builder()
                .stateKey("SYSTEM")
                .masterEnabled(true)
                .build();
        lenient().when(systemStateRepository.findById("SYSTEM"))
                .thenReturn(Optional.of(activeSystemState));
    }

    @Test
    @DisplayName("submitPairingRequest: new node creates a PENDING request")
    void testSubmitPairingRequest_NewNode_CreatesPending() {
        NodePairingSubmissionRequest request = NodePairingSubmissionRequest.builder()
                .nodeId("NODE-99")
                .macAddress("AA:BB:CC:DD:EE:99")
                .hardwareModel("ESP32-WROOM-32")
                .firmwareVersion("2.0.0")
                .build();

        when(edgeNodeRepository.findByNodeId("NODE-99")).thenReturn(Optional.empty());
        when(pairingRequestRepository.findFirstByNodeIdAndPairingStatusOrderByRequestedAtDesc("NODE-99", PairingStatus.PENDING))
                .thenReturn(Optional.empty());

        NodePairingRequest saved = NodePairingRequest.builder()
                .id(100L)
                .nodeId("NODE-99")
                .macAddress("AA:BB:CC:DD:EE:99")
                .hardwareModel("ESP32-WROOM-32")
                .firmwareVersion("2.0.0")
                .pairingStatus(PairingStatus.PENDING)
                .requestedAt(LocalDateTime.now())
                .build();

        when(pairingRequestRepository.save(any(NodePairingRequest.class))).thenReturn(saved);

        PairingRequestResponse response = nodePairingService.submitPairingRequest(request);

        assertNotNull(response);
        assertEquals(PairingStatus.PENDING, response.getPairingStatus());
        assertEquals("NODE-99", response.getNodeId());
        verify(pairingRequestRepository).save(any(NodePairingRequest.class));
    }

    @Test
    @DisplayName("submitPairingRequest: existing node creates a PENDING request for operator approval")
    void testSubmitPairingRequest_ExistingNode_CreatesPendingRequest() {
        NodePairingSubmissionRequest request = NodePairingSubmissionRequest.builder()
                .nodeId("NODE-C3E8")
                .macAddress("8C:94:DF:6F:C3:E8")
                .hardwareModel("ESP32-WROOM-32")
                .firmwareVersion("2.0.0")
                .build();

        CommunicationIdentity identity = CommunicationIdentity.builder()
                .identityType(CommunicationIdentityType.MAC_ADDRESS)
                .identityValue("8C:94:DF:6F:C3:E8")
                .macAddress("8C:94:DF:6F:C3:E8")
                .build();

        EdgeNode commissionedNode = EdgeNode.builder()
                .id(2L)
                .nodeId("NODE-C3E8")
                .identity(identity)
                .lifecycleState(NodeLifecycleState.COMMISSIONED)
                .healthState(HealthState.HEALTHY)
                .healthMetrics(NodeHealthMetrics.builder().build())
                .build();

        when(edgeNodeRepository.findByNodeId("NODE-C3E8")).thenReturn(Optional.of(commissionedNode));

        NodePairingRequest pendingEntity = NodePairingRequest.builder()
                .id(4L)
                .nodeId("NODE-C3E8")
                .macAddress("8C:94:DF:6F:C3:E8")
                .pairingStatus(PairingStatus.PENDING)
                .requestedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();

        when(pairingRequestRepository.findFirstByNodeIdAndPairingStatusOrderByRequestedAtDesc("NODE-C3E8", PairingStatus.PENDING))
                .thenReturn(Optional.empty());
        when(pairingRequestRepository.save(any(NodePairingRequest.class))).thenReturn(pendingEntity);

        PairingRequestResponse response = nodePairingService.submitPairingRequest(request);

        assertNotNull(response);
        assertEquals(PairingStatus.PENDING, response.getPairingStatus());
        assertEquals("NODE-C3E8", response.getNodeId());
    }

    @Test
    @DisplayName("approveAndAssignNode: successfully approves and cleans other pending requests")
    void testApproveAndAssignNode_Success() {
        NodePairingRequest targetRequest = NodePairingRequest.builder()
                .id(10L)
                .nodeId("NODE-C3E8")
                .pairingStatus(PairingStatus.PENDING)
                .requestedAt(LocalDateTime.now())
                .build();

        NodePairingRequest duplicatePending = NodePairingRequest.builder()
                .id(11L)
                .nodeId("NODE-C3E8")
                .pairingStatus(PairingStatus.PENDING)
                .requestedAt(LocalDateTime.now())
                .build();

        when(pairingRequestRepository.findById(10L)).thenReturn(Optional.of(targetRequest));
        when(pairingRequestRepository.findByPairingStatusOrderByRequestedAtDesc(PairingStatus.PENDING))
                .thenReturn(List.of(duplicatePending));

        RegisterNodeRequest registerReq = RegisterNodeRequest.builder()
                .nodeId("NODE-C3E8")
                .identityType(CommunicationIdentityType.MAC_ADDRESS)
                .identityValue("8C:94:DF:6F:C3:E8")
                .macAddress("8C:94:DF:6F:C3:E8")
                .monitoringZoneId(1L)
                .build();

        when(edgeNodeRepository.findByNodeId("NODE-C3E8")).thenReturn(Optional.empty());

        EdgeNodeResponse registeredResponse = EdgeNodeResponse.builder()
                .id(2L)
                .nodeId("NODE-C3E8")
                .lifecycleState(NodeLifecycleState.COMMISSIONED)
                .build();

        when(edgeNodeRegistryService.registerNode(registerReq)).thenReturn(registeredResponse);
        when(edgeNodeRegistryService.getNodeById(2L)).thenReturn(registeredResponse);

        EdgeNodeResponse result = nodePairingService.approveAndAssignNode(10L, registerReq, "Gladwin");

        assertNotNull(result);
        assertEquals("NODE-C3E8", result.getNodeId());
        assertEquals(PairingStatus.APPROVED, targetRequest.getPairingStatus());
        assertEquals(PairingStatus.APPROVED, duplicatePending.getPairingStatus());
        verify(pairingRequestRepository).save(targetRequest);
        verify(pairingRequestRepository).save(duplicatePending);
        verify(edgeNodeRegistryService).commissionNode(eq(2L), any());
    }

    @Test
    @DisplayName("getPairingStatusByNodeId: returns APPROVED for commissioned node")
    void testGetPairingStatusByNodeId_CommissionedNode() {
        EdgeNode node = EdgeNode.builder()
                .id(2L)
                .nodeId("NODE-C3E8")
                .lifecycleState(NodeLifecycleState.COMMISSIONED)
                .identity(CommunicationIdentity.builder().macAddress("8C:94:DF:6F:C3:E8").build())
                .build();

        when(edgeNodeRepository.findByNodeId("NODE-C3E8")).thenReturn(Optional.of(node));
        when(pairingRequestRepository.findFirstByNodeIdOrderByRequestedAtDesc("NODE-C3E8")).thenReturn(Optional.empty());

        PairingRequestResponse response = nodePairingService.getPairingStatusByNodeId("NODE-C3E8");

        assertNotNull(response);
        assertEquals("NODE-C3E8", response.getNodeId());
        assertEquals(PairingStatus.APPROVED, response.getPairingStatus());
    }

    @Test
    @DisplayName("approveAndAssignNode: throws ValidationException when pairing request is expired (>30 minutes)")
    void testApproveAndAssignNode_ExpiredRequest_ThrowsValidationException() {
        LocalDateTime requestedAt = LocalDateTime.now().minusMinutes(35);
        LocalDateTime expiresAt = requestedAt.plusMinutes(30);

        NodePairingRequest expiredReq = NodePairingRequest.builder()
                .id(999L)
                .nodeId("NODE-EXPIRED")
                .macAddress("AA:BB:CC:DD:EE:99")
                .pairingStatus(PairingStatus.PENDING)
                .requestedAt(requestedAt)
                .expiresAt(expiresAt)
                .build();

        when(pairingRequestRepository.findById(999L)).thenReturn(Optional.of(expiredReq));

        RegisterNodeRequest registerReq = RegisterNodeRequest.builder()
                .nodeId("NODE-EXPIRED")
                .identityType(CommunicationIdentityType.MAC_ADDRESS)
                .identityValue("AA:BB:CC:DD:EE:99")
                .build();

        com.aquaflow.backend.infrastructure.exception.ValidationException ex = assertThrows(
                com.aquaflow.backend.infrastructure.exception.ValidationException.class,
                () -> nodePairingService.approveAndAssignNode(999L, registerReq, "ADMIN")
        );

        assertTrue(ex.getMessage().contains("PAIRING_EXPIRED"));
        assertEquals(PairingStatus.EXPIRED, expiredReq.getPairingStatus());
        verify(pairingRequestRepository).save(expiredReq);
    }

    @Test
    @DisplayName("submitPairingRequest: duplicate request within window updates metadata without extending expiresAt")
    void testSubmitPairingRequest_DuplicateWithinWindow_PreservesExpiresAt() {
        LocalDateTime requestedAt = LocalDateTime.now().minusMinutes(10);
        LocalDateTime originalExpiresAt = requestedAt.plusMinutes(30);

        NodePairingRequest activePending = NodePairingRequest.builder()
                .id(50L)
                .nodeId("NODE-50")
                .macAddress("AA:BB:CC:DD:EE:50")
                .hardwareModel("ESP32-OLD")
                .firmwareVersion("1.0.0")
                .pairingStatus(PairingStatus.PENDING)
                .requestedAt(requestedAt)
                .expiresAt(originalExpiresAt)
                .build();

        when(edgeNodeRepository.findByNodeId("NODE-50")).thenReturn(Optional.empty());
        when(pairingRequestRepository.findFirstByNodeIdAndPairingStatusOrderByRequestedAtDesc("NODE-50", PairingStatus.PENDING))
                .thenReturn(Optional.of(activePending));
        when(pairingRequestRepository.save(any(NodePairingRequest.class))).thenAnswer(i -> i.getArgument(0));

        NodePairingSubmissionRequest submission = NodePairingSubmissionRequest.builder()
                .nodeId("NODE-50")
                .macAddress("AA:BB:CC:DD:EE:50")
                .hardwareModel("ESP32-NEW")
                .firmwareVersion("2.1.0")
                .build();

        PairingRequestResponse response = nodePairingService.submitPairingRequest(submission);

        assertNotNull(response);
        assertEquals("NODE-50", response.getNodeId());
        assertEquals("ESP32-NEW", response.getHardwareModel());
        assertEquals(originalExpiresAt, activePending.getExpiresAt());
        assertFalse(response.isExpired());
    }

    @Test
    @DisplayName("unpairNode: successfully unpairs node, transitions to UNREGISTERED, and revokes pairing")
    void testUnpairNode_Success() {
        EdgeNode node = EdgeNode.builder()
                .id(100L)
                .nodeId("NODE-UNPAIR")
                .lifecycleState(NodeLifecycleState.ACTIVE)
                .build();

        NodePairingRequest approvedReq = NodePairingRequest.builder()
                .id(100L)
                .nodeId("NODE-UNPAIR")
                .pairingStatus(PairingStatus.APPROVED)
                .requestedAt(LocalDateTime.now().minusDays(5))
                .expiresAt(LocalDateTime.now().minusDays(5).plusMinutes(30))
                .build();

        when(edgeNodeRepository.findByNodeId("NODE-UNPAIR")).thenReturn(Optional.of(node));
        when(pairingRequestRepository.findAll()).thenReturn(List.of(approvedReq));
        when(pairingRequestRepository.save(any(NodePairingRequest.class))).thenAnswer(i -> i.getArgument(0));
        when(edgeNodeRepository.save(any(EdgeNode.class))).thenAnswer(i -> i.getArgument(0));

        com.aquaflow.backend.dto.response.NodeUnpairResponse response =
                nodePairingService.unpairNode("NODE-UNPAIR", "Gladwin");

        assertNotNull(response);
        assertEquals("NODE-UNPAIR", response.getNodeId());
        assertTrue(response.isUnpaired());
        assertEquals(NodeLifecycleState.UNREGISTERED, node.getLifecycleState());
        assertEquals(PairingStatus.REVOKED, approvedReq.getPairingStatus());
        verify(auditLogRepository).save(any(SystemAuditLog.class));
        verify(systemEventPublisher).publishPairingRequestUpdated("NODE-UNPAIR", "REVOKED");
    }

    @Test
    @DisplayName("unpairNode: is safely repeatable for unknown or already uncommissioned node")
    void testUnpairNode_SafelyRepeatable() {
        when(edgeNodeRepository.findByNodeId("NODE-UNKNOWN")).thenReturn(Optional.empty());
        when(pairingRequestRepository.findAll()).thenReturn(Collections.emptyList());
        when(pairingRequestRepository.save(any(NodePairingRequest.class))).thenAnswer(i -> i.getArgument(0));

        com.aquaflow.backend.dto.response.NodeUnpairResponse response =
                nodePairingService.unpairNode("NODE-UNKNOWN", "admin");

        assertNotNull(response);
        assertEquals("NODE-UNKNOWN", response.getNodeId());
        assertTrue(response.isUnpaired());
    }

    @Test
    @DisplayName("approveAndAssignNode: throws ValidationException when pairing request is REVOKED")
    void testApproveAndAssignNode_RevokedRequest_ThrowsValidationException() {
        NodePairingRequest revokedReq = NodePairingRequest.builder()
                .id(555L)
                .nodeId("NODE-REVOKED")
                .pairingStatus(PairingStatus.REVOKED)
                .requestedAt(LocalDateTime.now().minusMinutes(5))
                .expiresAt(LocalDateTime.now().plusMinutes(25))
                .build();

        when(pairingRequestRepository.findById(555L)).thenReturn(Optional.of(revokedReq));

        RegisterNodeRequest registerReq = RegisterNodeRequest.builder()
                .nodeId("NODE-REVOKED")
                .build();

        com.aquaflow.backend.infrastructure.exception.ValidationException ex = assertThrows(
                com.aquaflow.backend.infrastructure.exception.ValidationException.class,
                () -> nodePairingService.approveAndAssignNode(555L, registerReq, "ADMIN")
        );

        assertTrue(ex.getMessage().contains("PAIRING_EXPIRED"));
    }

    @Test
    @DisplayName("getPairingStatusByNodeId: returns REVOKED for UNREGISTERED node")
    void testGetPairingStatusByNodeId_UnregisteredNode_ReturnsRevoked() {
        EdgeNode node = EdgeNode.builder()
                .id(200L)
                .nodeId("NODE-REVOKED-1")
                .lifecycleState(NodeLifecycleState.UNREGISTERED)
                .build();

        when(edgeNodeRepository.findByNodeId("NODE-REVOKED-1")).thenReturn(Optional.of(node));
        when(pairingRequestRepository.findFirstByNodeIdOrderByRequestedAtDesc("NODE-REVOKED-1")).thenReturn(Optional.empty());

        PairingRequestResponse response = nodePairingService.getPairingStatusByNodeId("NODE-REVOKED-1");

        assertNotNull(response);
        assertEquals("NODE-REVOKED-1", response.getNodeId());
        assertEquals(PairingStatus.REVOKED, response.getPairingStatus());
        assertFalse(response.isRecognized());
    }

    @Test
    @DisplayName("Accepted paired nodes never expire automatically beyond 30 minutes")
    void testAcceptedPairing_NeverExpiresAutomatically() {
        EdgeNode node = EdgeNode.builder()
                .id(300L)
                .nodeId("NODE-PERMANENT")
                .lifecycleState(NodeLifecycleState.ACTIVE)
                .build();

        // Node was approved 30 days ago
        NodePairingRequest oldApprovedReq = NodePairingRequest.builder()
                .id(300L)
                .nodeId("NODE-PERMANENT")
                .pairingStatus(PairingStatus.APPROVED)
                .requestedAt(LocalDateTime.now().minusDays(30))
                .expiresAt(LocalDateTime.now().minusDays(30).plusMinutes(30))
                .build();

        when(edgeNodeRepository.findByNodeId("NODE-PERMANENT")).thenReturn(Optional.of(node));
        when(pairingRequestRepository.findFirstByNodeIdOrderByRequestedAtDesc("NODE-PERMANENT"))
                .thenReturn(Optional.of(oldApprovedReq));

        PairingRequestResponse response = nodePairingService.getPairingStatusByNodeId("NODE-PERMANENT");

        assertNotNull(response);
        assertEquals(PairingStatus.APPROVED, response.getPairingStatus());
        assertFalse(response.isExpired());
    }

    @Test
    @DisplayName("Reset single node pairing revokes pairing and clears state to UNREGISTERED")
    void testResetNodePairing_SingleNode() {
        EdgeNode node = EdgeNode.builder()
                .id(501L)
                .nodeId("NODE-RESET-01")
                .lifecycleState(NodeLifecycleState.ACTIVE)
                .build();

        when(edgeNodeRepository.findByNodeId("NODE-RESET-01")).thenReturn(Optional.of(node));
        when(pairingRequestRepository.findAll()).thenReturn(List.of(
                NodePairingRequest.builder()
                        .id(901L)
                        .nodeId("NODE-RESET-01")
                        .pairingStatus(PairingStatus.APPROVED)
                        .build()
        ));

        com.aquaflow.backend.dto.response.NodePairingResetResponse response =
                nodePairingService.resetNodePairing("NODE-RESET-01", "Manual administrative reset", "Gladwin");

        assertNotNull(response);
        assertEquals(1, response.getTotalRequested());
        assertEquals(1, response.getSucceededCount());
        assertEquals(0, response.getFailedCount());
        assertTrue(response.getResetNodeIds().contains("NODE-RESET-01"));

        assertEquals(NodeLifecycleState.UNREGISTERED, node.getLifecycleState());
        verify(edgeNodeRepository).save(node);
        verify(pairingRequestRepository).save(any(NodePairingRequest.class));
        verify(systemEventPublisher).publishPairingRequestUpdated("NODE-RESET-01", "REVOKED");
    }

    @Test
    @DisplayName("Bulk reset handles multiple nodes with accurate success and failure tracking")
    void testResetPairing_Bulk() {
        EdgeNode node1 = EdgeNode.builder().id(601L).nodeId("NODE-01").lifecycleState(NodeLifecycleState.ACTIVE).build();
        EdgeNode node2 = EdgeNode.builder().id(602L).nodeId("NODE-02").lifecycleState(NodeLifecycleState.COMMISSIONED).build();

        when(edgeNodeRepository.findByNodeId("NODE-01")).thenReturn(Optional.of(node1));
        when(edgeNodeRepository.findByNodeId("NODE-02")).thenReturn(Optional.of(node2));
        when(pairingRequestRepository.findAll()).thenReturn(Collections.emptyList());

        com.aquaflow.backend.dto.request.NodePairingResetRequest req =
                com.aquaflow.backend.dto.request.NodePairingResetRequest.builder()
                        .nodeIds(List.of("NODE-01", "NODE-02", ""))
                        .reason("Network re-commissioning")
                        .build();

        com.aquaflow.backend.dto.response.NodePairingResetResponse response =
                nodePairingService.resetPairing(req, "Gladwin");

        assertNotNull(response);
        assertEquals(2, response.getTotalRequested());
        assertEquals(2, response.getSucceededCount());
        assertEquals(0, response.getFailedCount());
        assertEquals(List.of("NODE-01", "NODE-02"), response.getResetNodeIds());
    }

    @Test
    @DisplayName("Reset all pairings without specific node IDs revokes all pending requests and detaches zones")
    void testResetPairing_AllNodes_RevokesPendingAndClearsFieldAssignments() {
        MonitoringZone zone = MonitoringZone.builder().id(99L).name("Zone A").build();
        EdgeNode node1 = EdgeNode.builder().id(701L).nodeId("NODE-ALL-01").lifecycleState(NodeLifecycleState.ACTIVE).monitoringZone(zone).build();
        EdgeNode node2 = EdgeNode.builder().id(702L).nodeId("NODE-ALL-02").lifecycleState(NodeLifecycleState.UNREGISTERED).monitoringZone(zone).build();

        NodePairingRequest pendingReq = NodePairingRequest.builder().id(801L).nodeId("NODE-PENDING-01").pairingStatus(PairingStatus.PENDING).build();

        when(edgeNodeRepository.findAll()).thenReturn(List.of(node1, node2));
        when(edgeNodeRepository.findByNodeId("NODE-ALL-01")).thenReturn(Optional.of(node1));
        when(edgeNodeRepository.findByNodeId("NODE-ALL-02")).thenReturn(Optional.of(node2));
        when(pairingRequestRepository.findByPairingStatusOrderByRequestedAtDesc(PairingStatus.PENDING))
                .thenReturn(List.of(pendingReq));
        when(pairingRequestRepository.findAll()).thenReturn(List.of(pendingReq));

        com.aquaflow.backend.dto.response.NodePairingResetResponse response =
                nodePairingService.resetPairing(null, "ADMIN");

        assertNotNull(response);
        assertEquals(2, response.getTotalRequested());
        assertEquals(2, response.getSucceededCount());
        assertNull(node1.getMonitoringZone());
        assertNull(node2.getMonitoringZone());
        assertEquals(NodeLifecycleState.UNREGISTERED, node1.getLifecycleState());
        assertEquals(NodeLifecycleState.UNREGISTERED, node2.getLifecycleState());
        assertEquals(PairingStatus.REVOKED, pendingReq.getPairingStatus());
        verify(pairingRequestRepository, atLeastOnce()).save(pendingReq);
    }
}
