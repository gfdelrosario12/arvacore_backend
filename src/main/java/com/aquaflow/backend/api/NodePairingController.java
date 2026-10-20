package com.aquaflow.backend.api;

import com.aquaflow.backend.domain.NodePairingService;
import com.aquaflow.backend.dto.request.MasterStateUpdateRequest;
import com.aquaflow.backend.dto.request.NodePairingSubmissionRequest;
import com.aquaflow.backend.dto.request.PairingCompletionRequest;
import com.aquaflow.backend.dto.request.PairingRejectionRequest;
import com.aquaflow.backend.dto.request.RegisterNodeRequest;
import com.aquaflow.backend.dto.response.*;
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
    // Node Pairing Bridge & Device Authentication Endpoints
    // -------------------------------------------------------------
    @PostMapping({"/pairing/requests", "/pairing/request"})
    public ResponseEntity<PairingRequestResponse> submitPairingRequest(
            @Valid @RequestBody NodePairingSubmissionRequest request) {
        log.info("Pairing request received for node: {}", request.getNodeId());
        PairingRequestResponse response = nodePairingService.submitPairingRequest(request);
        if (response.getId() != null) {
            return ResponseEntity.created(URI.create("/api/v1/pairing/requests/" + response.getId())).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pairing/requests/pending")
    public ResponseEntity<List<PairingRequestResponse>> getPendingRequests() {
        return ResponseEntity.ok(nodePairingService.getPendingPairingRequests());
    }

    @GetMapping("/pairing/requests")
    public ResponseEntity<List<PairingRequestResponse>> getRequests(
            @RequestParam(value = "status", required = false) String status) {
        if ("PENDING".equalsIgnoreCase(status)) {
            return ResponseEntity.ok(nodePairingService.getPendingPairingRequests());
        }
        return ResponseEntity.ok(nodePairingService.getAllPairingRequests());
    }

    @GetMapping("/pairing/requests/{id}")
    public ResponseEntity<PairingRequestResponse> getPairingRequest(@PathVariable Long id) {
        return ResponseEntity.ok(nodePairingService.getPairingRequest(id));
    }

    @GetMapping({"/pairing/devices/{nodeId}/status", "/pairing/status/{nodeId}"})
    public ResponseEntity<PairingRequestResponse> getPairingStatusByNodeId(@PathVariable String nodeId) {
        log.info("Pairing status check received for nodeId: {}", nodeId);
        return ResponseEntity.ok(nodePairingService.getPairingStatusByNodeId(nodeId));
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

    @PostMapping("/pairing/requests/{id}/reject")
    public ResponseEntity<PairingRequestResponse> rejectPairingRequest(
            @PathVariable Long id,
            @RequestBody(required = false) PairingRejectionRequest request,
            Authentication authentication) {
        String actor = (authentication != null && authentication.getName() != null)
                ? authentication.getName() : "ADMIN";
        return ResponseEntity.ok(nodePairingService.rejectPairingRequest(id, request, actor));
    }

    @PostMapping("/pairing/requests/{id}/provision")
    public ResponseEntity<PairingProvisionResponse> provisionOperationalCredential(
            @PathVariable Long id,
            Authentication authentication) {
        String actor = (authentication != null && authentication.getName() != null)
                ? authentication.getName() : "DEVICE_BOOTSTRAP";
        log.info("Operational credential provisioning requested for pairing request #{} by {}", id, actor);
        return ResponseEntity.ok(nodePairingService.provisionOperationalCredential(id, actor));
    }

    @PostMapping("/pairing/requests/{id}/complete")
    public ResponseEntity<PairingRequestResponse> completePairing(
            @PathVariable Long id,
            @RequestBody(required = false) PairingCompletionRequest request,
            Authentication authentication) {
        String actor = (authentication != null && authentication.getName() != null)
                ? authentication.getName() : "DEVICE_OPERATIONAL";
        log.info("Pairing completion confirmation received for request #{} by {}", id, actor);
        return ResponseEntity.ok(nodePairingService.completePairing(id, request, actor));
    }

    @PostMapping({"/nodes/{nodeId}/unpair", "/pairing/{nodeId}/unpair"})
    @DeleteMapping("/nodes/{nodeId}/pairing")
    public ResponseEntity<NodeUnpairResponse> unpairNode(
            @PathVariable String nodeId,
            Authentication authentication) {
        String actor = (authentication != null && authentication.getName() != null)
                ? authentication.getName() : "ADMIN";
        log.info("Unpairing request received for nodeId {} by {}", nodeId, actor);
        return ResponseEntity.ok(nodePairingService.unpairNode(nodeId, actor));
    }

    @PostMapping("/pairing/reset")
    public ResponseEntity<com.aquaflow.backend.dto.response.NodePairingResetResponse> resetPairing(
            @RequestBody(required = false) com.aquaflow.backend.dto.request.NodePairingResetRequest request,
            Authentication authentication) {
        String actor = (authentication != null && authentication.getName() != null)
                ? authentication.getName() : "ADMIN";
        return ResponseEntity.ok(nodePairingService.resetPairing(request, actor));
    }
}
