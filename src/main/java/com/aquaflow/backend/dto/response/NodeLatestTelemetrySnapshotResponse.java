package com.aquaflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Batched response holding the latest telemetry snapshot of every requested node.
 *
 * <p>Served by {@code GET /api/v1/nodes/telemetry/latest} so the ArvaLink Home Dashboard
 * can refresh every visible node with a single request instead of one request per node.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeLatestTelemetrySnapshotResponse {

    /** Server-side time the snapshot was produced. */
    private LocalDateTime serverTime;

    /** One entry per node visible to the caller; empty when the caller sees no nodes. */
    private List<NodeLatestTelemetryResponse> nodes;
}
