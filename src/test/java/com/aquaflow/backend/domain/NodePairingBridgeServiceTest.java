package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.NodePairingSubmissionRequest;
import com.aquaflow.backend.dto.request.PairingCompletionRequest;
import com.aquaflow.backend.dto.response.PairingProvisionResponse;
import com.aquaflow.backend.dto.response.PairingRequestResponse;
import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.infrastructure.event.SystemEventPublisher;
import com.aquaflow.backend.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NodePairingBridgeServiceTest {

    @Mock
    private SystemStateRepository systemStateRepository;

    @Mock
    private NodePairingRequestRepository pairingRequestRepository;

    @Mock
    private EdgeNodeRepository edgeNodeRepository;

    @Mock
    private EdgeNodeRegistryService edgeNodeRegistryService;

    @Mock
    private SystemAuditLogRepository auditLogRepository;

    @Mock
    private SystemEventPublisher systemEventPublisher;

    @Mock
    private DeviceCredentialRepository deviceCredentialRepository;

    @InjectMocks
    private NodePairingServiceImpl nodePairingService;

    private final String nodeId = "ARVANODE-001";

    @BeforeEach
    void setUp() {
        SystemState masterState = SystemState.builder().stateKey("SYSTEM").masterEnabled(true).build();
        lenient().when(systemStateRepository.findById("SYSTEM")).thenReturn(Optional.of(masterState));
    }

    @Test
    @DisplayName("Already-paired node in ACTIVE state returns ALREADY_PAIRED without creating new request")
    void testAlreadyPairedNodeReturnsAlreadyPaired() {
        EdgeNode activeNode = EdgeNode.builder()
                .id(10L)
                .nodeId(nodeId)
                .identity(CommunicationIdentity.builder().macAddress("AA:BB:CC:DD:EE:01").build())
                .lifecycleState(NodeLifecycleState.ACTIVE)
                .hardwareModel("ESP32-WROOM-32")
                .firmwareVersion("1.2.0")
                .build();

        DeviceCredential operationalCred = DeviceCredential.builder()
                .nodeId(nodeId)
                .credentialState(DeviceCredentialState.OPERATIONAL)
                .build();

        lenient().when(edgeNodeRepository.findByNodeId(nodeId)).thenReturn(Optional.of(activeNode));
        lenient().when(deviceCredentialRepository.findByNodeId(nodeId)).thenReturn(Optional.of(operationalCred));
        lenient().when(pairingRequestRepository.findFirstByNodeIdOrderByRequestedAtDesc(nodeId)).thenReturn(Optional.empty());

        NodePairingSubmissionRequest req = NodePairingSubmissionRequest.builder()
                .nodeId(nodeId)
                .macAddress("AA:BB:CC:DD:EE:01")
                .hardwareModel("ESP32-WROOM-32")
                .firmwareVersion("1.2.0")
                .build();

        PairingRequestResponse response = nodePairingService.submitPairingRequest(req);

        assertNotNull(response);
        assertEquals(PairingStatus.ALREADY_PAIRED, response.getPairingStatus());
        assertTrue(response.isPaired());
        assertEquals("RESUME_OPERATION", response.getAction());
        verify(pairingRequestRepository, never()).save(any(NodePairingRequest.class));
    }

    @Test
    @DisplayName("Active PENDING request is reused without extending expiration")
    void testActivePendingRequestIsReused() {
        lenient().when(edgeNodeRepository.findByNodeId(nodeId)).thenReturn(Optional.empty());
        lenient().when(deviceCredentialRepository.findByNodeId(nodeId)).thenReturn(Optional.empty());

        LocalDateTime originalExpiry = LocalDateTime.now().plusMinutes(25);
        NodePairingRequest existingPending = NodePairingRequest.builder()
                .id(101L)
                .nodeId(nodeId)
                .pairingStatus(PairingStatus.PENDING)
                .requestedAt(LocalDateTime.now().minusMinutes(5))
                .expiresAt(originalExpiry)
                .build();

        lenient().when(pairingRequestRepository.findFirstByNodeIdOrderByRequestedAtDesc(nodeId))
                .thenReturn(Optional.of(existingPending));
        lenient().when(pairingRequestRepository.findFirstByNodeIdAndPairingStatusOrderByRequestedAtDesc(nodeId, PairingStatus.APPROVED))
                .thenReturn(Optional.empty());
        lenient().when(pairingRequestRepository.findFirstByNodeIdAndPairingStatusOrderByRequestedAtDesc(nodeId, PairingStatus.PROVISIONING))
                .thenReturn(Optional.empty());
        lenient().when(pairingRequestRepository.findFirstByNodeIdAndPairingStatusOrderByRequestedAtDesc(nodeId, PairingStatus.PENDING))
                .thenReturn(Optional.of(existingPending));
        lenient().when(pairingRequestRepository.save(any(NodePairingRequest.class))).thenAnswer(i -> i.getArgument(0));

        NodePairingSubmissionRequest req = NodePairingSubmissionRequest.builder()
                .nodeId(nodeId)
                .macAddress("AA:BB:CC:DD:EE:01")
                .hardwareModel("ESP32-WROOM-32")
                .firmwareVersion("1.2.0")
                .build();

        PairingRequestResponse response = nodePairingService.submitPairingRequest(req);

        assertNotNull(response);
        assertEquals(101L, response.getId());
        assertEquals("WAIT_FOR_APPROVAL", response.getAction());
        assertFalse(response.isPaired());
        assertEquals(originalExpiry, response.getExpiresAt());
    }

    @Test
    @DisplayName("Approved provisioning establishes operational credential and completion marks node PAIRED")
    void testProvisionAndCompletePairing() {
        NodePairingRequest approvedReq = NodePairingRequest.builder()
                .id(202L)
                .nodeId(nodeId)
                .pairingStatus(PairingStatus.APPROVED)
                .requestedAt(LocalDateTime.now().minusMinutes(2))
                .expiresAt(LocalDateTime.now().plusMinutes(28))
                .build();

        DeviceCredential bootstrapCred = DeviceCredential.builder()
                .nodeId(nodeId)
                .bootstrapKeyId("bootstrap-" + nodeId)
                .bootstrapSecret("dev_sec_bootstrap")
                .credentialState(DeviceCredentialState.BOOTSTRAP)
                .build();

        lenient().when(pairingRequestRepository.findById(202L)).thenReturn(Optional.of(approvedReq));
        lenient().when(deviceCredentialRepository.findByNodeId(nodeId)).thenReturn(Optional.of(bootstrapCred));
        lenient().when(pairingRequestRepository.save(any(NodePairingRequest.class))).thenAnswer(i -> i.getArgument(0));

        PairingProvisionResponse provResp = nodePairingService.provisionOperationalCredential(202L, "ESP32_BOOTSTRAP");

        assertNotNull(provResp);
        assertNotNull(provResp.getOperationalKeyId());
        assertNotNull(provResp.getOperationalSecret());
        assertEquals(PairingStatus.PROVISIONING, provResp.getStatus());
        assertEquals("CONFIRM", provResp.getAction());

        // Now complete pairing
        EdgeNode registeredNode = EdgeNode.builder()
                .id(50L)
                .nodeId(nodeId)
                .lifecycleState(NodeLifecycleState.PROVISIONED)
                .build();
        lenient().when(edgeNodeRepository.findByNodeId(nodeId)).thenReturn(Optional.of(registeredNode));

        PairingCompletionRequest compReq = PairingCompletionRequest.builder()
                .operationalKeyId(provResp.getOperationalKeyId())
                .notes("ESP32 confirmation")
                .build();

        PairingRequestResponse compResp = nodePairingService.completePairing(202L, compReq, "ESP32_OPERATIONAL");

        assertNotNull(compResp);
        assertEquals(PairingStatus.PAIRED, compResp.getPairingStatus());
        assertTrue(compResp.isPaired());
        assertEquals("RESUME_OPERATION", compResp.getAction());
        assertEquals(NodeLifecycleState.ACTIVE, registeredNode.getLifecycleState());
        assertEquals(DeviceCredentialState.OPERATIONAL, bootstrapCred.getCredentialState());
    }

    @Test
    @DisplayName("Unpairing node revokes operational credential and edge node lifecycle")
    void testUnpairingRevokesCredential() {
        EdgeNode node = EdgeNode.builder()
                .id(10L)
                .nodeId(nodeId)
                .lifecycleState(NodeLifecycleState.ACTIVE)
                .build();

        DeviceCredential cred = DeviceCredential.builder()
                .nodeId(nodeId)
                .operationalKeyId("oper-123")
                .operationalSecret("oper_sec_123")
                .credentialState(DeviceCredentialState.OPERATIONAL)
                .build();

        lenient().when(edgeNodeRepository.findByNodeId(nodeId)).thenReturn(Optional.of(node));
        lenient().when(deviceCredentialRepository.findByNodeId(nodeId)).thenReturn(Optional.of(cred));

        nodePairingService.unpairNode(nodeId, "Gladwin");

        assertEquals(NodeLifecycleState.UNREGISTERED, node.getLifecycleState());
        assertEquals(DeviceCredentialState.REVOKED, cred.getCredentialState());
        assertNull(cred.getOperationalSecret());
    }
}
