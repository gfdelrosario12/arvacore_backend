package com.aquaflow.backend.service;

import com.aquaflow.backend.domain.*;
import com.aquaflow.backend.dto.DecodedTelemetryPayload;
import com.aquaflow.backend.dto.SignalMetadataDto;
import com.aquaflow.backend.dto.request.TelemetryBatchUplinkRequest;
import com.aquaflow.backend.dto.request.TelemetryUplinkRequest;
import com.aquaflow.backend.dto.response.TelemetryBatchUplinkResponse;
import com.aquaflow.backend.dto.response.TelemetryUplinkResponse;
import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.infrastructure.exception.ValidationException;
import com.aquaflow.backend.persistence.EdgeNodeRepository;
import com.aquaflow.backend.persistence.MonitoringPointRepository;
import com.aquaflow.backend.persistence.TelemetryReadingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TelemetryIngestionServiceImplTest {

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
    private com.aquaflow.backend.infrastructure.event.SystemEventPublisher systemEventPublisher;

    @Mock
    private com.aquaflow.backend.domain.NodePairingService nodePairingService;

    private TelemetryIngestionServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        org.mockito.Mockito.lenient().when(nodePairingService.isMasterEnabled()).thenReturn(true);
        service = new TelemetryIngestionServiceImpl(
                edgeNodeRepository,
                monitoringPointRepository,
                telemetryReadingRepository,
                edgeNodeRegistryService,
                payloadDecoderRegistry,
                systemEventPublisher,
                nodePairingService
        );
    }

    @Test
    void shouldIngestTelemetrySuccessfullyAndUpdateNodeMetrics() {
        TelemetryUplinkRequest request = TelemetryUplinkRequest.builder()
                .nodeId("NODE-001")
                .fCnt(10L)
                .build();

        DecodedTelemetryPayload decoded = DecodedTelemetryPayload.builder()
                .nodeId("NODE-001")
                .fCnt(10L)
                .timestamp(LocalDateTime.now())
                .batteryLevel(92.0)
                .solarVoltage(5.1)
                .signalMetadata(SignalMetadataDto.builder().rssiDbm(-65).build())
                .measurements(Map.of(SensorType.SOIL_MOISTURE, 38.0))
                .build();

        EdgeNode node = EdgeNode.builder()
                .id(1L)
                .nodeId("NODE-001")
                .lifecycleState(NodeLifecycleState.COMMISSIONED)
                .healthState(HealthState.HEALTHY)
                .healthMetrics(new NodeHealthMetrics())
                .build();

        when(payloadDecoderRegistry.decode(request)).thenReturn(decoded);
        when(edgeNodeRepository.findByNodeId("NODE-001")).thenReturn(Optional.of(node));
        when(monitoringPointRepository.findByEdgeNodeId(1L)).thenReturn(Collections.emptyList());

        TelemetryUplinkResponse response = service.ingestUplink(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isDuplicate()).isFalse();
        assertThat(response.getReadingsIngested()).isEqualTo(1);
        verify(edgeNodeRegistryService, times(1)).validateNodeActiveForOperations(1L);
        verify(telemetryReadingRepository, times(1)).save(any(TelemetryReading.class));
        verify(edgeNodeRepository, times(1)).save(node);
    }

    @Test
    void shouldDetectDuplicateFrameCounterAndIgnore() {
        TelemetryUplinkRequest request1 = TelemetryUplinkRequest.builder().nodeId("NODE-001").fCnt(10L).build();
        TelemetryUplinkRequest request2 = TelemetryUplinkRequest.builder().nodeId("NODE-001").fCnt(10L).build();

        DecodedTelemetryPayload decoded = DecodedTelemetryPayload.builder().nodeId("NODE-001").fCnt(10L).measurements(Map.of()).build();
        EdgeNode node = EdgeNode.builder().id(1L).nodeId("NODE-001").lifecycleState(NodeLifecycleState.COMMISSIONED).build();

        when(payloadDecoderRegistry.decode(any(TelemetryUplinkRequest.class))).thenReturn(decoded);
        when(edgeNodeRepository.findByNodeId("NODE-001")).thenReturn(Optional.of(node));
        when(monitoringPointRepository.findByEdgeNodeId(1L)).thenReturn(Collections.emptyList());

        service.ingestUplink(request1);
        TelemetryUplinkResponse response2 = service.ingestUplink(request2);

        assertThat(response2.isSuccess()).isTrue();
        assertThat(response2.isDuplicate()).isTrue();
        assertThat(response2.getReadingsIngested()).isEqualTo(0);
    }

    @Test
    void shouldThrowExceptionWhenNodeIsInactive() {
        TelemetryUplinkRequest request = TelemetryUplinkRequest.builder().nodeId("NODE-DECOMMISSIONED").build();
        DecodedTelemetryPayload decoded = DecodedTelemetryPayload.builder().nodeId("NODE-DECOMMISSIONED").build();
        EdgeNode node = EdgeNode.builder().id(2L).nodeId("NODE-DECOMMISSIONED").lifecycleState(NodeLifecycleState.DECOMMISSIONED).build();

        when(payloadDecoderRegistry.decode(request)).thenReturn(decoded);
        when(edgeNodeRepository.findByNodeId("NODE-DECOMMISSIONED")).thenReturn(Optional.of(node));
        doThrow(new ValidationException("NODE_NOT_ACTIVE: Node is in inactive lifecycle state: DECOMMISSIONED"))
                .when(edgeNodeRegistryService).validateNodeActiveForOperations(2L);

        assertThatThrownBy(() -> service.ingestUplink(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("NODE_NOT_ACTIVE");
    }

    @Test
    void shouldIngestBatchUplinks() {
        TelemetryUplinkRequest req1 = TelemetryUplinkRequest.builder().nodeId("NODE-001").fCnt(1L).build();
        TelemetryBatchUplinkRequest batchRequest = TelemetryBatchUplinkRequest.builder().uplinks(List.of(req1)).build();

        DecodedTelemetryPayload decoded = DecodedTelemetryPayload.builder().nodeId("NODE-001").fCnt(1L).measurements(Map.of()).build();
        EdgeNode node = EdgeNode.builder().id(1L).nodeId("NODE-001").lifecycleState(NodeLifecycleState.COMMISSIONED).build();

        when(payloadDecoderRegistry.decode(req1)).thenReturn(decoded);
        when(edgeNodeRepository.findByNodeId("NODE-001")).thenReturn(Optional.of(node));

        TelemetryBatchUplinkResponse batchResponse = service.ingestBatchUplink(batchRequest);

        assertThat(batchResponse.getTotalProcessed()).isEqualTo(1);
        assertThat(batchResponse.getSuccessful()).isEqualTo(1);
        assertThat(batchResponse.getFailed()).isEqualTo(0);
    }
}

