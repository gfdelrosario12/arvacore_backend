package com.aquaflow.backend.service;

import com.aquaflow.backend.domain.TelemetryStreamServiceImpl;
import com.aquaflow.backend.dto.response.TelemetryStreamEventResponse;
import com.aquaflow.backend.dto.response.TelemetryStreamSummaryResponse;
import com.aquaflow.backend.entity.TelemetryStreamEvent;
import com.aquaflow.backend.entity.Zone;
import com.aquaflow.backend.infrastructure.event.SystemEventPublisher;
import com.aquaflow.backend.persistence.TelemetryStreamEventRepository;
import com.aquaflow.backend.persistence.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryStreamServiceTest {

    @Mock
    private TelemetryStreamEventRepository telemetryStreamEventRepository;

    @Mock
    private SystemEventPublisher systemEventPublisher;

    @Mock
    private ZoneRepository zoneRepository;

    private TelemetryStreamServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TelemetryStreamServiceImpl(
                telemetryStreamEventRepository,
                systemEventPublisher,
                zoneRepository
        );
    }

    @Test
    void shouldRecordStreamEventAndBroadcastViaSystemEventPublisher() {
        TelemetryStreamEvent event = TelemetryStreamEvent.builder()
                .nodeId("NODE-C3E8")
                .status("PROCESSED")
                .soilMoisturePercent(45.59)
                .waterLevelDistanceCm(123.15)
                .waterLevelCm(-23.15)
                .batteryVoltage(4.15)
                .batteryLevel(95.0)
                .sequenceNumber(7L)
                .build();

        when(telemetryStreamEventRepository.save(any(TelemetryStreamEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TelemetryStreamEvent saved = service.recordStreamEvent(event);

        assertNotNull(saved);
        assertNotNull(saved.getEventId());
        assertNotNull(saved.getReceivedAt());
        assertEquals("NODE-C3E8", saved.getNodeId());
        assertEquals("PROCESSED", saved.getStatus());
        assertEquals(7L, saved.getSequenceNumber());

        verify(telemetryStreamEventRepository).save(event);
        verify(systemEventPublisher).publishTelemetryStreamEvent(saved);
    }

    @Test
    void shouldGetSummaryWithAggregatedMetrics() {
        when(telemetryStreamEventRepository.count()).thenReturn(142L);
        when(telemetryStreamEventRepository.countByReceivedAtAfter(any())).thenReturn(28L);
        when(telemetryStreamEventRepository.countDistinctNodeId()).thenReturn(3L);
        when(telemetryStreamEventRepository.countFailedOrRejected()).thenReturn(2L);
        LocalDateTime now = LocalDateTime.now();
        when(telemetryStreamEventRepository.findFirstByOrderByReceivedAtDesc())
                .thenReturn(Optional.of(TelemetryStreamEvent.builder().receivedAt(now).build()));

        TelemetryStreamSummaryResponse summary = service.getSummary();

        assertNotNull(summary);
        assertEquals(142L, summary.getTotalTransmissions());
        assertEquals(28L, summary.getRecentTransmissionsCount());
        assertEquals(3L, summary.getDistinctNodesCount());
        assertEquals(2L, summary.getFailedOrRejectedCount());
        assertEquals(now, summary.getLastEventReceivedAt());
        assertEquals(3, summary.getActiveConnectionsCount());
    }

    @Test
    void shouldGetRecentEventsUsingLimit() {
        TelemetryStreamEvent event = TelemetryStreamEvent.builder()
                .eventId("evt-1")
                .nodeId("NODE-01")
                .status("PROCESSED")
                .receivedAt(LocalDateTime.now())
                .build();

        when(telemetryStreamEventRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        List<TelemetryStreamEventResponse> recent = service.getRecentEvents(10, null);

        assertEquals(1, recent.size());
        assertEquals("NODE-01", recent.get(0).getNodeId());
    }

    @Test
    void shouldGetFilterOptions() {
        when(telemetryStreamEventRepository.findDistinctNodeIds()).thenReturn(List.of("NODE-A", "NODE-B"));
        when(zoneRepository.findAll()).thenReturn(List.of(Zone.builder().id(1L).name("Zone 1").build()));

        Map<String, Object> filters = service.getFilterOptions();

        assertTrue(filters.containsKey("nodeIds"));
        assertTrue(filters.containsKey("statuses"));
        assertTrue(filters.containsKey("zones"));
        assertEquals(List.of("NODE-A", "NODE-B"), filters.get("nodeIds"));
    }
}

