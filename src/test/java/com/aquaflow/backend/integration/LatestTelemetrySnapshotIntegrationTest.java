package com.aquaflow.backend.integration;

import com.aquaflow.backend.domain.EdgeNodeRegistryService;
import com.aquaflow.backend.domain.TelemetryIngestionService;
import com.aquaflow.backend.dto.request.TelemetryUplinkRequest;
import com.aquaflow.backend.dto.response.NodeLatestTelemetryResponse;
import com.aquaflow.backend.dto.response.NodeLatestTelemetrySnapshotResponse;
import com.aquaflow.backend.dto.response.TelemetryUplinkResponse;
import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the batched "latest telemetry per node" read path used by the ArvaLink Home
 * Dashboard. The dashboard polls this endpoint on a fixed interval, so the query must
 * return exactly one reading per (node, sensor type) pair and must not depend on the
 * amount of telemetry history stored for a node.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class LatestTelemetrySnapshotIntegrationTest {

    @Autowired
    private EdgeNodeRegistryService edgeNodeRegistryService;

    @Autowired
    private TelemetryIngestionService telemetryIngestionService;

    @Autowired
    private EdgeNodeRepository edgeNodeRepository;

    @Autowired
    private MonitoringZoneRepository monitoringZoneRepository;

    @Autowired
    private MonitoringPointRepository monitoringPointRepository;

    @Autowired
    private FieldRepository fieldRepository;

    @Autowired
    private TelemetryReadingRepository telemetryReadingRepository;

    private EdgeNode nodeA;
    private EdgeNode nodeB;
    private MonitoringZone zone;

    @BeforeEach
    void setUp() {
        Field field = fieldRepository.save(Field.builder()
                .name("Snapshot Test Field")
                .boundaryGeoJson("{\"type\":\"Polygon\",\"coordinates\":[]}")
                .build());

        zone = monitoringZoneRepository.save(MonitoringZone.builder()
                .name("Zone Snapshot")
                .field(field)
                .cropType("RICE")
                .targetMoisturePercentage(80.0)
                .waterAllocationLimitLiters(5000.0)
                .build());

        nodeA = createNode("SNAP-001", zone);
        nodeB = createNode("SNAP-002", zone);
    }

    private EdgeNode createNode(String nodeId, MonitoringZone monitoringZone) {
        EdgeNode node = edgeNodeRepository.save(EdgeNode.builder()
                .nodeId(nodeId)
                .identity(CommunicationIdentity.builder()
                        .identityType(CommunicationIdentityType.LORA_DEVEUI)
                        .identityValue("0004A30B" + nodeId.hashCode())
                        .build())
                .lifecycleState(NodeLifecycleState.ACTIVE)
                .healthState(HealthState.HEALTHY)
                .healthMetrics(NodeHealthMetrics.builder()
                        .batteryLevel(88.0)
                        .soilSensorHealth("OK")
                        .ultrasonicSensorHealth("OK")
                        .telemetryStatus("OK")
                        .lastHeartbeat(LocalDateTime.now())
                        .lastTelemetryAt(LocalDateTime.now())
                        .isTelemetryStale(false)
                        .build())
                .monitoringZone(monitoringZone)
                .hardwareModel("ESP32-LoRa-V2")
                .firmwareVersion("v1.2.0")
                .build());

        monitoringPointRepository.save(MonitoringPoint.builder()
                .name("MP-" + nodeId)
                .monitoringZone(monitoringZone)
                .edgeNode(node)
                .primarySensorType(SensorType.SOIL_MOISTURE)
                .depthCm(15.0)
                .build());

        return node;
    }

    private TelemetryReading saveReading(EdgeNode node, SensorType type, double value, LocalDateTime timestamp) {
        return telemetryReadingRepository.save(TelemetryReading.builder()
                .monitoringPoint(monitoringPointRepository.findByEdgeNodeId(node.getId()).stream().findFirst().orElse(null))
                .edgeNode(node)
                .sensorType(type)
                .valueNum(value)
                .unit("%")
                .timestamp(timestamp)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Test
    void testLatestSnapshot_ReturnsNewestReadingPerNodeAndSensorType() {
        LocalDateTime older = LocalDateTime.now().minusMinutes(30);
        LocalDateTime newer = LocalDateTime.now().minusMinutes(1);

        saveReading(nodeA, SensorType.SOIL_MOISTURE, 41.0, older);
        saveReading(nodeA, SensorType.SOIL_MOISTURE, 62.5, newer);
        saveReading(nodeA, SensorType.WATER_LEVEL_DISTANCE, 30.0, older);
        saveReading(nodeA, SensorType.WATER_LEVEL_DISTANCE, 45.0, newer);
        saveReading(nodeB, SensorType.SOIL_MOISTURE, 0.0, newer);

        NodeLatestTelemetrySnapshotResponse snapshot = edgeNodeRegistryService.getLatestTelemetrySnapshot(null);

        assertThat(snapshot.getNodes()).hasSize(2);
        assertThat(snapshot.getServerTime()).isNotNull();

        NodeLatestTelemetryResponse first = snapshot.getNodes().stream()
                .filter(n -> "SNAP-001".equals(n.getNodeId()))
                .findFirst()
                .orElseThrow();
        NodeLatestTelemetryResponse second = snapshot.getNodes().stream()
                .filter(n -> "SNAP-002".equals(n.getNodeId()))
                .findFirst()
                .orElseThrow();

        assertThat(first.getSoilMoisturePercent()).isEqualTo(62.5);
        assertThat(first.getWaterLevelDistanceCm()).isEqualTo(45.0);
        assertThat(first.getWaterLevelCm()).isEqualTo(56.6);
        assertThat(first.getMonitoringZoneId()).isEqualTo(zone.getId());
        assertThat(first.getHasTelemetry()).isTrue();

        // A reported numeric zero must survive the snapshot, it is not "missing data".
        assertThat(second.getSoilMoisturePercent()).isEqualTo(0.0);
        assertThat(second.getHasTelemetry()).isTrue();
    }

    @Test
    void testLatestSnapshot_ScopeCanBeRestrictedToRequestedNodes() {
        saveReading(nodeA, SensorType.SOIL_MOISTURE, 55.0, LocalDateTime.now());
        saveReading(nodeB, SensorType.SOIL_MOISTURE, 66.0, LocalDateTime.now());

        NodeLatestTelemetrySnapshotResponse snapshot =
                edgeNodeRegistryService.getLatestTelemetrySnapshot(List.of("SNAP-002"));

        assertThat(snapshot.getNodes()).hasSize(1);
        assertThat(snapshot.getNodes().get(0).getNodeId()).isEqualTo("SNAP-002");
        assertThat(snapshot.getNodes().get(0).getSoilMoisturePercent()).isEqualTo(66.0);
    }

    @Test
    void testLatestSnapshot_UnknownNodeIsRejected() {
        assertThatThrownBy(() -> edgeNodeRegistryService.getLatestTelemetrySnapshot(List.of("MISSING-NODE")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testLatestSnapshot_NodeWithoutTelemetryReportsNoValues() {
        NodeLatestTelemetrySnapshotResponse snapshot =
                edgeNodeRegistryService.getLatestTelemetrySnapshot(List.of("SNAP-001"));

        assertThat(snapshot.getNodes()).hasSize(1);
        NodeLatestTelemetryResponse node = snapshot.getNodes().get(0);
        assertThat(node.getHasTelemetry()).isFalse();
        assertThat(node.getSoilMoisturePercent()).isNull();
        assertThat(node.getWaterLevelDistanceCm()).isNull();
        assertThat(node.getWaterLevelCm()).isNull();
    }

    @Test
    void testGetNodes_DoesNotStreamFullTelemetryHistory() {
        LocalDateTime base = LocalDateTime.now().minusMinutes(60);
        for (int i = 0; i < 25; i++) {
            saveReading(nodeA, SensorType.SOIL_MOISTURE, 30.0 + i, base.plusMinutes(i));
        }

        List<com.aquaflow.backend.dto.response.EdgeNodeResponse> nodes =
                edgeNodeRegistryService.getNodes(null, null, null);

        assertThat(nodes).isNotEmpty();
        com.aquaflow.backend.dto.response.EdgeNodeResponse first = nodes.stream()
                .filter(n -> "SNAP-001".equals(n.getNodeId()))
                .findFirst()
                .orElseThrow();
        // Only the newest reading is surfaced, never the historical series.
        assertThat(first.getSoilMoisturePercent()).isEqualTo(54.0);
    }

    /**
     * Verifies the complete ESP32 → backend → database → read-API path: a real uplink is
     * ingested and the dashboard snapshot has to reflect it immediately, and has to follow
     * the newest uplink after a second transmission.
     */
    @Test
    void testLatestSnapshot_FollowsRealTelemetryUplinks() {
        TelemetryUplinkResponse first = telemetryIngestionService.ingestUplink(
                TelemetryUplinkRequest.builder()
                        .nodeId("SNAP-001")
                        .batteryLevel(91.0)
                        .batteryVoltage(4.05)
                        .timestamp(LocalDateTime.now())
                        .measurements(Map.of(
                                "SOIL_MOISTURE", 48.0,
                                "WATER_LEVEL_DISTANCE", 40.0,
                                "TEMPERATURE", 27.5
                        ))
                        .build());
        assertThat(first.isSuccess()).isTrue();

        NodeLatestTelemetryResponse afterFirst = snapshotFor("SNAP-001");
        assertThat(afterFirst.getSoilMoisturePercent()).isEqualTo(48.0);
        assertThat(afterFirst.getWaterLevelDistanceCm()).isEqualTo(40.0);
        assertThat(afterFirst.getHasTelemetry()).isTrue();

        TelemetryUplinkResponse second = telemetryIngestionService.ingestUplink(
                TelemetryUplinkRequest.builder()
                        .nodeId("SNAP-001")
                        .batteryLevel(90.0)
                        .batteryVoltage(4.02)
                        .timestamp(LocalDateTime.now())
                        .measurements(Map.of(
                                "SOIL_MOISTURE", 12.5,
                                "WATER_LEVEL_DISTANCE", 55.0
                        ))
                        .build());
        assertThat(second.isSuccess()).isTrue();

        NodeLatestTelemetryResponse afterSecond = snapshotFor("SNAP-001");
        assertThat(afterSecond.getSoilMoisturePercent()).isEqualTo(12.5);
        assertThat(afterSecond.getWaterLevelDistanceCm()).isEqualTo(55.0);
        // The snapshot timestamp moves forward with the newest uplink.
        assertThat(afterSecond.getLastTelemetryAt())
                .isAfterOrEqualTo(afterFirst.getLastTelemetryAt());
    }

    @Test
    void testLatestSnapshot_ReportsZeroWithoutTreatingItAsMissing() {
        TelemetryUplinkResponse response = telemetryIngestionService.ingestUplink(
                TelemetryUplinkRequest.builder()
                        .nodeId("SNAP-002")
                        .batteryLevel(70.0)
                        .timestamp(LocalDateTime.now())
                        .measurements(Map.of(
                                "SOIL_MOISTURE", 0.0,
                                "WATER_LEVEL_DISTANCE", 0.0
                        ))
                        .build());
        assertThat(response.isSuccess()).isTrue();

        NodeLatestTelemetryResponse snapshot = snapshotFor("SNAP-002");
        assertThat(snapshot.getSoilMoisturePercent()).isEqualTo(0.0);
        assertThat(snapshot.getWaterLevelDistanceCm()).isEqualTo(0.0);
        assertThat(snapshot.getWaterLevelCm()).isEqualTo(0.0);
        assertThat(snapshot.getHasTelemetry()).isTrue();
    }

    private NodeLatestTelemetryResponse snapshotFor(String nodeId) {
        return edgeNodeRegistryService.getLatestTelemetrySnapshot(List.of(nodeId))
                .getNodes()
                .stream()
                .filter(n -> nodeId.equals(n.getNodeId()))
                .findFirst()
                .orElseThrow();
    }
}
