package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.SensorType;
import com.aquaflow.backend.entity.TelemetryReading;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TelemetryReadingRepository extends JpaRepository<TelemetryReading, Long> {
    List<TelemetryReading> findByMonitoringPointId(Long monitoringPointId);
    Page<TelemetryReading> findByMonitoringPointId(Long monitoringPointId, Pageable pageable);
    Page<TelemetryReading> findByMonitoringPointIdAndTimestampBetween(Long monitoringPointId, LocalDateTime from, LocalDateTime to, Pageable pageable);
    Page<TelemetryReading> findByEdgeNodeId(Long edgeNodeId, Pageable pageable);
    List<TelemetryReading> findByEdgeNodeIdOrderByTimestampDesc(Long edgeNodeId);
    Page<TelemetryReading> findByEdgeNodeIdAndTimestampBetween(Long edgeNodeId, LocalDateTime from, LocalDateTime to, Pageable pageable);
    List<TelemetryReading> findByMonitoringPointIdAndSensorTypeOrderByTimestampDesc(Long monitoringPointId, SensorType sensorType);

    /**
     * Resolves the newest reading of every sensor type for each of the supplied nodes in a
     * single statement.
     *
     * <p>This replaces the previous per-node {@code findByEdgeNodeIdOrderByTimestampDesc}
     * lookups, which loaded the entire telemetry history of every node and produced an
     * N+1 query pattern on {@code GET /api/v1/nodes}. The correlated {@code MAX(timestamp)}
     * subquery is served by {@code idx_telemetry_readings_node_type_ts}
     * (edge_node_id, sensor_type, timestamp DESC).</p>
     *
     * @param edgeNodeIds identifiers of the nodes whose latest readings are requested
     * @return at most one reading per (node, sensor type) pair
     */
    @Query("""
            SELECT r FROM TelemetryReading r
            WHERE r.edgeNode.id IN :edgeNodeIds
              AND r.timestamp = (
                    SELECT MAX(r2.timestamp) FROM TelemetryReading r2
                    WHERE r2.edgeNode.id = r.edgeNode.id
                      AND r2.sensorType = r.sensorType
              )
            """)
    List<TelemetryReading> findLatestReadingsByEdgeNodeIds(
            @Param("edgeNodeIds") Collection<Long> edgeNodeIds);
}
