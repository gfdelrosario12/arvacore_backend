package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.TelemetryUplinkRequest;
import com.aquaflow.backend.dto.response.TelemetryUplinkResponse;
import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.infrastructure.event.SystemEventPublisher;
import com.aquaflow.backend.infrastructure.exception.ValidationException;
import com.aquaflow.backend.persistence.EdgeNodeRepository;
import com.aquaflow.backend.persistence.MonitoringPointRepository;
import com.aquaflow.backend.persistence.TelemetryReadingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SixNodeTelemetryIngestionTest {

    @Mock
    private EdgeNodeRepository edgeNodeRepository;
    @Mock
    private MonitoringPointRepository monitoringPointRepository;
    @Mock
    private TelemetryReadingRepository telemetryReadingRepository;
    @Mock
    private EdgeNodeRegistryService edgeNodeRegistryService;
    @Mock
    private PayloadDecoderRegistry payloadDecoderRegistry;
    @Mock
    private SystemEventPublisher systemEventPublisher;
    @Mock
    private NodePairingService nodePairingService;
    @Mock
    private TelemetryStreamService telemetryStreamService;

    private TelemetryIngestionServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new TelemetryIngestionServiceImpl(
                edgeNodeRepository,
                monitoringPointRepository,
                telemetryReadingRepository,
                edgeNodeRegistryService,
                payloadDecoderRegistry,
                systemEventPublisher,
                nodePairingService,
                telemetryStreamService,
                new ObjectMapper()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARVANODE-001", "ARVANODE-002", "ARVANODE-003", "ARVANODE-004", "ARVANODE-005", "ARVANODE-006"})
    void shouldIngestTelemetryForAuthorizedSixNodes(String nodeId) {
        int nodeNum = Integer.parseInt(nodeId.substring(11));
        long zoneId = (long) nodeNum;

        Field field = Field.builder().id(1L).name("Main Rice Field").build();
        MonitoringZone zone = MonitoringZone.builder().id(zoneId).code("Q" + zoneId).field(field).build();
        EdgeNode node = EdgeNode.builder()
                .id((long) nodeNum)
                .nodeId(nodeId)
                .monitoringZone(zone)
                .lifecycleState(NodeLifecycleState.COMMISSIONED)
                .healthState(HealthState.HEALTHY)
                .build();

        when(edgeNodeRepository.findByNodeId(nodeId)).thenReturn(Optional.of(node));

        java.util.Map<SensorType, Double> measurements = new java.util.HashMap<>();
        measurements.put(SensorType.SOIL_MOISTURE, 65.0);
        measurements.put(SensorType.WATER_LEVEL_DISTANCE, 15.0);
        measurements.put(SensorType.BATTERY_VOLTAGE, 4.1);

        com.aquaflow.backend.dto.DecodedTelemetryPayload decoded = com.aquaflow.backend.dto.DecodedTelemetryPayload.builder()
                .nodeId(nodeId)
                .timestamp(LocalDateTime.now())
                .fCnt(100L)
                .batteryLevel(95.0)
                .measurements(measurements)
                .build();
        when(payloadDecoderRegistry.decode(any(TelemetryUplinkRequest.class))).thenReturn(decoded);

        TelemetryUplinkRequest request = TelemetryUplinkRequest.builder()
                .nodeId(nodeId)
                .gatewayId("ARVAGATEWAY-001")
                .waterLevel(5.0)
                .soilMoisturePercent(65.0)
                .fCnt(100L)
                .build();

        TelemetryUplinkResponse response = service.ingestUplinkForNode(nodeId, request);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getNodeId()).isEqualTo(nodeId);
        verify(telemetryReadingRepository, atLeastOnce()).save(any(TelemetryReading.class));
    }

    @Test
    void shouldRejectFixedZoneMismatch() {
        // ARVANODE-003 assigned to Zone 1 instead of Zone 3
        Field field = Field.builder().id(1L).name("Main Rice Field").build();
        MonitoringZone wrongZone = MonitoringZone.builder().id(1L).code("Q1").field(field).build();
        EdgeNode node = EdgeNode.builder()
                .id(3L)
                .nodeId("ARVANODE-003")
                .monitoringZone(wrongZone)
                .lifecycleState(NodeLifecycleState.COMMISSIONED)
                .build();

        when(edgeNodeRepository.findByNodeId("ARVANODE-003")).thenReturn(Optional.of(node));

        com.aquaflow.backend.dto.DecodedTelemetryPayload decoded = com.aquaflow.backend.dto.DecodedTelemetryPayload.builder()
                .nodeId("ARVANODE-003")
                .timestamp(LocalDateTime.now())
                .build();
        when(payloadDecoderRegistry.decode(any())).thenReturn(decoded);

        TelemetryUplinkRequest request = TelemetryUplinkRequest.builder()
                .nodeId("ARVANODE-003")
                .waterLevel(5.0)
                .build();

        assertThatThrownBy(() -> service.ingestUplinkForNode("ARVANODE-003", request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("FIXED_ZONE_MISMATCH");
    }

    @Test
    void shouldDetectAndSuppressDuplicateFrameCounters() {
        Field field = Field.builder().id(1L).name("Main Rice Field").build();
        MonitoringZone zone = MonitoringZone.builder().id(4L).code("Q4").field(field).build();
        EdgeNode node = EdgeNode.builder()
                .id(4L)
                .nodeId("ARVANODE-004")
                .monitoringZone(zone)
                .lifecycleState(NodeLifecycleState.COMMISSIONED)
                .build();

        when(edgeNodeRepository.findByNodeId("ARVANODE-004")).thenReturn(Optional.of(node));

        com.aquaflow.backend.dto.DecodedTelemetryPayload decoded = com.aquaflow.backend.dto.DecodedTelemetryPayload.builder()
                .nodeId("ARVANODE-004")
                .timestamp(LocalDateTime.now())
                .fCnt(50L)
                .build();
        when(payloadDecoderRegistry.decode(any())).thenReturn(decoded);

        TelemetryUplinkRequest request = TelemetryUplinkRequest.builder()
                .nodeId("ARVANODE-004")
                .fCnt(50L)
                .waterLevel(3.2)
                .soilMoisturePercent(60.0)
                .build();

        // First ingestion: Success
        TelemetryUplinkResponse resp1 = service.ingestUplinkForNode("ARVANODE-004", request);
        assertThat(resp1.isSuccess()).isTrue();
        assertThat(resp1.isDuplicate()).isFalse();

        // Second ingestion with same or lower fCnt: Duplicate suppressed
        TelemetryUplinkResponse resp2 = service.ingestUplinkForNode("ARVANODE-004", request);
        assertThat(resp2.isSuccess()).isTrue();
        assertThat(resp2.isDuplicate()).isTrue();
        assertThat(resp2.getMessage()).contains("Duplicate frame counter");
    }
}
