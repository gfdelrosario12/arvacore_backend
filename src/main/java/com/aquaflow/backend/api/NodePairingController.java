package com.aquaflow.backend.api;

import com.aquaflow.backend.domain.NodePairingService;
import com.aquaflow.backend.dto.request.MasterStateUpdateRequest;
import com.aquaflow.backend.dto.request.NodePairingSubmissionRequest;
import com.aquaflow.backend.dto.request.PairingRejectionRequest;
import com.aquaflow.backend.dto.request.RegisterNodeRequest;
import com.aquaflow.backend.dto.response.CoreHealthResponse;
import com.aquaflow.backend.dto.response.EdgeNodeResponse;
import com.aquaflow.backend.dto.response.MasterStateResponse;
import com.aquaflow.backend.dto.response.PairingRequestResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class NodePairingController {

    private final NodePairingService nodePairingService;

    // -------------------------------------------------------------
    // Master System Control Endpoints
    // -------------------------------------------------------------
    @GetMapping("/system/master-state")
    public ResponseEntity<MasterStateResponse> getMasterState() {
        return ResponseEntity.ok(nodePairingService.getMasterState());
    }

    @PutMapping("/system/master-state")
    public ResponseEntity<MasterStateResponse> updateMasterState(
            @Valid @RequestBody MasterStateUpdateRequest request,
            Authentication authentication) {
        String actor = (authentication != null && authentication.getName() != null)
                ? authentication.getName() : "ADMIN";
        return ResponseEntity.ok(nodePairingService.updateMasterState(request, actor));
    }

    @GetMapping("/system/health")
    public ResponseEntity<CoreHealthResponse> getCoreHealth() {
        return ResponseEntity.ok(nodePairingService.getCoreHealth());
    }

    // -------------------------------------------------------------
    // Node Pairing & Approval Endpoints
    // -------------------------------------------------------------
    @PostMapping("/pairing/request")
    public ResponseEntity<PairingRequestResponse> submitPairingRequest(
            @Valid @RequestBody NodePairingSubmissionRequest request) {
        log.info("Pairing request received for node: {}", request.getNodeId());
        PairingRequestResponse response = nodePairingService.submitPairingRequest(request);
        return ResponseEntity.created(URI.create("/api/v1/pairing/requests/" + response.getId())).body(response);
    }

    @GetMapping("/pairing/requests/pending")
    public ResponseEntity<List<PairingRequestResponse>> getPendingRequests() {
        return ResponseEntity.ok(nodePairingService.getPendingPairingRequests());
    }

    @GetMapping("/pairing/requests")
    public ResponseEntity<List<PairingRequestResponse>> getAllRequests() {
        return ResponseEntity.ok(nodePairingService.getAllPairingRequests());
    }

    @GetMapping("/pairing/requests/{id}")
    public ResponseEntity<PairingRequestResponse> getPairingRequest(@PathVariable Long id) {
        return ResponseEntity.ok(nodePairingService.getPairingRequest(id));
    }

    @PostMapping("/pairing/requests/{id}/reject")
    public ResponseEntity<PairingRequestResponse> rejectPairingRequest(
            @PathVariable Long id,
            @RequestBody(required = false) PairingRejectionRequest request,
            Authentication authentication) {
        String actor = (authentication != null && authentication.getName() != null)
                ? authentication.getName() : "ADMIN";
        return ResponseEntity.ok(nodePairingService.rejectPairingRequest(id, request, actor));
    }

    @PostMapping("/pairing/requests/{id}/approve")
    public ResponseEntity<EdgeNodeResponse> approveAndAssign(
            @PathVariable Long id,
            @Valid @RequestBody RegisterNodeRequest request,
            Authentication authentication) {
        String actor = (authentication != null && authentication.getName() != null)
                ? authentication.getName() : "ADMIN";
        return ResponseEntity.ok(nodePairingService.approveAndAssignNode(id, request, actor));
    }
}

