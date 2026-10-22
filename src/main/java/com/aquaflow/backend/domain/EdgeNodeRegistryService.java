package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.*;
import com.aquaflow.backend.dto.response.*;
import com.aquaflow.backend.entity.HealthState;
import com.aquaflow.backend.entity.NodeLifecycleState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

public interface EdgeNodeRegistryService {

    EdgeNodeResponse registerNode(RegisterNodeRequest request);

    List<EdgeNodeResponse> getNodes(NodeLifecycleState lifecycleState, HealthState healthState, Long zoneId);

    Page<EdgeNodeResponse> getNodesPaginated(NodeLifecycleState lifecycleState, HealthState healthState, Long zoneId, Pageable pageable);

    EdgeNodeResponse getNodeById(Long id);

    EdgeNodeResponse updateNode(Long id, RegisterNodeRequest request);

    EdgeNodeResponse assignNodeToField(Long id, AssignNodeFieldRequest request);
    EdgeNodeResponse assignNodeToField(String idOrNodeId, AssignNodeFieldRequest request);

    CommissionNodeResponse commissionNode(Long id, CommissionNodeRequest request);

    EdgeNodeResponse decommissionNode(Long id);

    EdgeNodeResponse replaceNode(Long faultyNodeId, ReplaceNodeRequest request);

    EdgeNodeResponse updateTransmissionConfig(Long id, UpdateTransmissionRequest request);

    NodeHealthResponse getNodeHealth(Long id);

    NodeHealthSummaryResponse getNodeHealthSummary();

    List<TelemetryReadingResponse> getLatestTelemetry(Long id);

    /**
     * Returns the latest telemetry snapshot of every node visible to the caller.
     *
     * @param requestedNodeIds optional node identifiers (canonical node id or numeric id) to
     *                          restrict the snapshot to; {@code null} or empty returns every
     *                          visible node
     * @return the batched snapshot
     */
    NodeLatestTelemetrySnapshotResponse getLatestTelemetrySnapshot(Collection<String> requestedNodeIds);

    Page<TelemetryReadingResponse> getHistoricalTelemetry(Long id, Pageable pageable);

    void validateNodeActiveForOperations(Long id);

    void validateNodeActiveForOperations(String nodeId);

    List<NodeAssignmentHistoryResponse> getNodeAssignmentHistory(Long id);

    List<NodeAssignmentHistoryResponse> getNodeAssignmentHistory(String nodeId);
}

