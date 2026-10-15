package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.MasterStateUpdateRequest;
import com.aquaflow.backend.dto.request.NodePairingSubmissionRequest;
import com.aquaflow.backend.dto.request.PairingRejectionRequest;
import com.aquaflow.backend.dto.request.RegisterNodeRequest;
import com.aquaflow.backend.dto.response.CoreHealthResponse;
import com.aquaflow.backend.dto.response.EdgeNodeResponse;
import com.aquaflow.backend.dto.response.MasterStateResponse;
import com.aquaflow.backend.dto.response.PairingRequestResponse;

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

    // Core & Node System Overview Health
    CoreHealthResponse getCoreHealth();
}

