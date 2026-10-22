package com.aquaflow.backend.domain;

import com.aquaflow.backend.entity.AutoIrrigationConfig;
import com.aquaflow.backend.entity.EdgeNode;
import com.aquaflow.backend.entity.Field;
import com.aquaflow.backend.entity.MonitoringZone;
import com.aquaflow.backend.infrastructure.event.SystemEventPublisher;
import com.aquaflow.backend.persistence.AutoIrrigationConfigRepository;
import com.aquaflow.backend.persistence.FieldRepository;
import com.aquaflow.backend.persistence.IrrigationDecisionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThresholdEvaluationServiceTest {

    @Mock
    private AutoIrrigationConfigRepository autoConfigRepository;

    @Mock
    private FieldRepository fieldRepository;

    @Mock
    private DownlinkQueueService downlinkQueueService;

    @Mock
    private AlertService alertService;

    @Mock
    private SystemEventPublisher systemEventPublisher;

    @Mock
    private IrrigationDecisionRepository decisionRepository;

    private ThresholdEvaluationServiceImpl service;
    private EdgeNode testNode;
    private AutoIrrigationConfig testConfig;

    @BeforeEach
    void setUp() {
        service = new ThresholdEvaluationServiceImpl(
                autoConfigRepository,
                fieldRepository,
                downlinkQueueService,
                alertService,
                systemEventPublisher,
                decisionRepository,
                new ObjectMapper()
        );

        Field field = Field.builder().id(1L).name("Test Field").build();
        MonitoringZone zone = MonitoringZone.builder().id(10L).name("Zone A").code("Z-01").field(field).build();
        testNode = EdgeNode.builder().id(5L).nodeId("NODE-TEST-01").monitoringZone(zone).build();

        testConfig = AutoIrrigationConfig.builder()
                .id(1L)
                .field(field)
                .enabled(true)
                .minSoilMoisturePercentage(30.0)
                .maxSoilMoisturePercentage(70.0)
                .targetFloodDepthCm(5.0)
                .maxDurationMinutes(30)
                .minCooldownMinutes(0)
                .build();
    }

    @Test
    @DisplayName("Sensor failure must never trigger START_IRRIGATION")
    void testSensorFailureNeverTriggersStart() {
        when(autoConfigRepository.findByFieldId(1L)).thenReturn(Optional.of(testConfig));

        service.evaluateTelemetryThresholds(
                testNode,
                0.0, // fallback zero moisture
                0.0, // fallback zero level
                "DISCONNECTED", // sensor health failed
                "TIMEOUT",      // ultrasonic health failed
                "SENSOR_ERROR"
        );

        verify(downlinkQueueService, never()).queueDownlink(any(), contains("START_IRRIGATION"), any());
    }

    @Test
    @DisplayName("Critical water deficiency triggers START_IRRIGATION")
    void testCriticalWaterDeficiencyTriggersStart() {
        when(autoConfigRepository.findByFieldId(1L)).thenReturn(Optional.of(testConfig));

        service.evaluateTelemetryThresholds(
                testNode,
                15.0, // Below critical moisture (< 20%)
                -16.0, // Water level below -15 cm
                "OK",
                "OK",
                "OK"
        );

        verify(downlinkQueueService, atLeastOnce()).queueDownlink(eq(testNode), contains("START_IRRIGATION"), anyString());
    }

    @Test
    @DisplayName("Excess water triggers STOP_IRRIGATION")
    void testExcessWaterTriggersStop() {
        when(autoConfigRepository.findByFieldId(1L)).thenReturn(Optional.of(testConfig));

        service.evaluateTelemetryThresholds(
                testNode,
                50.0,
                12.0, // Excess water > 8.0 cm
                "OK",
                "OK",
                "OK"
        );

        verify(downlinkQueueService, atLeastOnce()).queueDownlink(eq(testNode), contains("STOP_IRRIGATION"), anyString());
    }
}
