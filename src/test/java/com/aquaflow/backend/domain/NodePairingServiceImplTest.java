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
    @DisplayName("submitPairingRequest: already commissioned node returns APPROVED immediately")
    void testSubmitPairingRequest_AlreadyCommissioned_ReturnsApproved() {
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

        NodePairingRequest existingApproved = NodePairingRequest.builder()
                .id(4L)
                .nodeId("NODE-C3E8")
                .macAddress("8C:94:DF:6F:C3:E8")
                .pairingStatus(PairingStatus.APPROVED)
                .requestedAt(LocalDateTime.now())
                .build();

        when(pairingRequestRepository.findFirstByNodeIdAndPairingStatusOrderByRequestedAtDesc("NODE-C3E8", PairingStatus.APPROVED))
                .thenReturn(Optional.of(existingApproved));
        when(pairingRequestRepository.save(any(NodePairingRequest.class))).thenReturn(existingApproved);
        when(pairingRequestRepository.findByPairingStatusOrderByRequestedAtDesc(PairingStatus.PENDING))
                .thenReturn(Collections.emptyList());

        PairingRequestResponse response = nodePairingService.submitPairingRequest(request);

        assertNotNull(response);
        assertEquals(PairingStatus.APPROVED, response.getPairingStatus());
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
}
