package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.MasterStateUpdateRequest;
import com.aquaflow.backend.dto.request.NodePairingSubmissionRequest;
import com.aquaflow.backend.dto.request.PairingCompletionRequest;
import com.aquaflow.backend.dto.request.PairingRejectionRequest;
import com.aquaflow.backend.dto.request.RegisterNodeRequest;
import com.aquaflow.backend.dto.response.*;

import java.util.List;

public interface NodePairingService {

    // Master System State
    MasterStateResponse getMasterState();
    MasterStateResponse updateMasterState(MasterStateUpdateRequest request, String actor);
    boolean isMasterEnabled();

    // Node Pairing Workflow
    PairingRequestResponse submitPairingRequest(NodePairingSubmissionRequest request);
    List<PairingRequestResponse> getPendingPairingRequests();
    List<PairingRequestResponse> getAllPairingRequests();
    PairingRequestResponse getPairingRequest(Long id);
    PairingRequestResponse getPairingStatusByNodeId(String nodeId);
    PairingRequestResponse rejectPairingRequest(Long id, PairingRejectionRequest request, String actor);
    EdgeNodeResponse approveAndAssignNode(Long pairingRequestId, RegisterNodeRequest request, String actor);
    PairingProvisionResponse provisionOperationalCredential(Long pairingRequestId, String actor);
    PairingRequestResponse completePairing(Long pairingRequestId, PairingCompletionRequest request, String actor);
    NodeUnpairResponse unpairNode(String nodeId, String actor);
    NodePairingResetResponse resetPairing(com.aquaflow.backend.dto.request.NodePairingResetRequest request, String actor);
    NodePairingResetResponse resetNodePairing(String nodeId, String reason, String actor);

    // Core & Node System Overview Health
    CoreHealthResponse getCoreHealth();
}
