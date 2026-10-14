package com.aquaflow.backend.controller;

import com.aquaflow.backend.api.NodePairingController;
import com.aquaflow.backend.domain.NodePairingService;
import com.aquaflow.backend.dto.request.MasterStateUpdateRequest;
import com.aquaflow.backend.dto.request.NodePairingSubmissionRequest;
import com.aquaflow.backend.dto.request.PairingRejectionRequest;
import com.aquaflow.backend.dto.request.RegisterNodeRequest;
import com.aquaflow.backend.dto.response.*;
import com.aquaflow.backend.entity.CommunicationIdentityType;
import com.aquaflow.backend.entity.PairingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class NodePairingControllerTest {

    @Mock
    private NodePairingService nodePairingService;

    @Mock
    private Authentication authentication;

    private NodePairingController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(authentication.getName()).thenReturn("admin_user");
        controller = new NodePairingController(nodePairingService);
    }

    @Test
    void shouldGetMasterState() {
        MasterStateResponse response = MasterStateResponse.builder()
                .masterEnabled(true)
                .updatedBy("ADMIN")
                .updatedAt(LocalDateTime.now())
                .build();
        when(nodePairingService.getMasterState()).thenReturn(response);

        ResponseEntity<MasterStateResponse> result = controller.getMasterState();
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isMasterEnabled()).isTrue();
    }

    @Test
    void shouldUpdateMasterState() {
        MasterStateUpdateRequest request = MasterStateUpdateRequest.builder()
                .enabled(false)
                .notes("Testing shutdown")
                .build();
        MasterStateResponse response = MasterStateResponse.builder()
                .masterEnabled(false)
                .updatedBy("admin_user")
                .notes("Testing shutdown")
                .updatedAt(LocalDateTime.now())
                .build();
        when(nodePairingService.updateMasterState(eq(request), eq("admin_user"))).thenReturn(response);

        ResponseEntity<MasterStateResponse> result = controller.updateMasterState(request, authentication);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isMasterEnabled()).isFalse();
    }

    @Test
    void shouldSubmitPairingRequest() {
        NodePairingSubmissionRequest request = NodePairingSubmissionRequest.builder()
                .nodeId("NODE-01")
                .macAddress("AA:BB:CC:DD:EE:01")
                .hardwareModel("ESP32-WROOM-32")
                .firmwareVersion("1.0.0")
                .build();
        PairingRequestResponse response = PairingRequestResponse.builder()
                .id(100L)
                .nodeId("NODE-01")
                .pairingStatus(PairingStatus.PENDING)
                .requestedAt(LocalDateTime.now())
                .build();
        when(nodePairingService.submitPairingRequest(any())).thenReturn(response);

        ResponseEntity<PairingRequestResponse> result = controller.submitPairingRequest(request);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getId()).isEqualTo(100L);
    }

    @Test
    void shouldGetPendingRequests() {
        PairingRequestResponse req = PairingRequestResponse.builder()
                .id(1L)
                .nodeId("NODE-01")
                .pairingStatus(PairingStatus.PENDING)
                .build();
        when(nodePairingService.getPendingPairingRequests()).thenReturn(List.of(req));

        ResponseEntity<List<PairingRequestResponse>> result = controller.getPendingRequests();
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
    }

    @Test
    void shouldRejectPairingRequest() {
        PairingRejectionRequest request = PairingRejectionRequest.builder().reason("Unauthorized").build();
        PairingRequestResponse response = PairingRequestResponse.builder()
                .id(1L)
                .nodeId("NODE-01")
                .pairingStatus(PairingStatus.REJECTED)
                .rejectionReason("Unauthorized")
                .build();
        when(nodePairingService.rejectPairingRequest(eq(1L), any(), eq("admin_user"))).thenReturn(response);

        ResponseEntity<PairingRequestResponse> result = controller.rejectPairingRequest(1L, request, authentication);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getPairingStatus()).isEqualTo(PairingStatus.REJECTED);
    }

    @Test
    void shouldApproveAndAssign() {
        RegisterNodeRequest request = RegisterNodeRequest.builder()
                .nodeId("NODE-01")
                .identityType(CommunicationIdentityType.MAC_ADDRESS)
                .identityValue("AA:BB:CC:DD:EE:01")
                .monitoringZoneId(1L)
                .build();
        EdgeNodeResponse nodeResponse = EdgeNodeResponse.builder()
                .id(5L)
                .nodeId("NODE-01")
                .monitoringZoneId(1L)
                .build();
        when(nodePairingService.approveAndAssignNode(eq(1L), any(), eq("admin_user"))).thenReturn(nodeResponse);

        ResponseEntity<EdgeNodeResponse> result = controller.approveAndAssign(1L, request, authentication);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getId()).isEqualTo(5L);
        assertThat(result.getBody().getMonitoringZoneId()).isEqualTo(1L);
    }
}
