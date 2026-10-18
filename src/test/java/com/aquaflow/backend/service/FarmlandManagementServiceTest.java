package com.aquaflow.backend.service;

import com.aquaflow.backend.domain.*;
import com.aquaflow.backend.dto.request.*;
import com.aquaflow.backend.dto.response.*;
import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FarmlandManagementServiceTest {

    @Mock
    private FieldRepository fieldRepository;

    @Mock
    private MonitoringZoneRepository monitoringZoneRepository;

    @Mock
    private EdgeNodeRepository edgeNodeRepository;

    @Mock
    private NodeAssignmentHistoryRepository nodeAssignmentHistoryRepository;

    @Mock
    private MonitoringPointRepository monitoringPointRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private ZoneAggregationService zoneAggregationService;

    private FarmlandValidationServiceImpl farmlandValidationService;
    private ZoneServiceImpl zoneService;

    @BeforeEach
    void setUp() {
        farmlandValidationService = new FarmlandValidationServiceImpl(
                fieldRepository, monitoringZoneRepository, edgeNodeRepository
        );
        zoneService = new ZoneServiceImpl(
                zoneRepository, monitoringZoneRepository, edgeNodeRepository, zoneAggregationService,
                fieldRepository, nodeAssignmentHistoryRepository, monitoringPointRepository, farmlandValidationService
        );
    }

    @Test
    @DisplayName("Should detect draft/incomplete status when field has no zones")
    void shouldDetectDraftStatusWhenFieldHasNoZones() {
        Field field = Field.builder()
                .id(1L)
                .name("Maligaya Farm Lot 4")
                .totalArea(1.25)
                .lengthMeters(125.0)
                .widthMeters(100.0)
                .operationalStatus("DRAFT")
                .build();

        when(fieldRepository.findById(1L)).thenReturn(Optional.of(field));
        when(monitoringZoneRepository.findByFieldIdAndIsArchivedFalse(1L)).thenReturn(Collections.emptyList());
        when(edgeNodeRepository.findAll()).thenReturn(Collections.emptyList());

        FarmlandValidationResponse result = farmlandValidationService.validateFarmlandReadiness(1L);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getOperationalStatus()).isEqualTo("DRAFT");
        assertThat(result.getBlockingIssues()).contains("No monitoring zones or quarters configured for this field.");
    }

    @Test
    @DisplayName("Should report ready status when zones exist with valid dimensions and no nodes")
    void shouldReportReadyStatusWhenZonesExistWithoutNodes() {
        Field field = Field.builder()
                .id(1L)
                .name("Maligaya Farm Lot 4")
                .totalArea(1.25)
                .lengthMeters(125.0)
                .widthMeters(100.0)
                .build();

        MonitoringZone zone1 = MonitoringZone.builder()
                .id(10L)
                .name("Quarter 1")
                .code("Q1")
                .field(field)
                .areaSize(0.3125)
                .isArchived(false)
                .build();

        when(fieldRepository.findById(1L)).thenReturn(Optional.of(field));
        when(monitoringZoneRepository.findByFieldIdAndIsArchivedFalse(1L)).thenReturn(List.of(zone1));
        when(edgeNodeRepository.findByMonitoringZoneId(10L)).thenReturn(Collections.emptyList());
        when(edgeNodeRepository.findAll()).thenReturn(Collections.emptyList());

        FarmlandValidationResponse result = farmlandValidationService.validateFarmlandReadiness(1L);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getOperationalStatus()).isEqualTo("READY");
        assertThat(result.getAdvisoryIssues()).anyMatch(issue -> issue.contains("has no ArvaNode assigned"));
    }

    @Test
    @DisplayName("Should report monitoring status when zones have active nodes")
    void shouldReportMonitoringStatusWhenNodesAreTransmitting() {
        Field field = Field.builder()
                .id(1L)
                .name("Maligaya Farm Lot 4")
                .totalArea(1.25)
                .lengthMeters(125.0)
                .widthMeters(100.0)
                .build();

        MonitoringZone zone1 = MonitoringZone.builder()
                .id(10L)
                .name("Quarter 1")
                .code("Q1")
                .field(field)
                .areaSize(0.3125)
                .isArchived(false)
                .build();

        EdgeNode node1 = EdgeNode.builder()
                .id(101L)
                .nodeId("NODE-01")
                .lifecycleState(NodeLifecycleState.COMMISSIONED)
                .healthState(HealthState.HEALTHY)
                .monitoringZone(zone1)
                .build();

        when(fieldRepository.findById(1L)).thenReturn(Optional.of(field));
        when(monitoringZoneRepository.findByFieldIdAndIsArchivedFalse(1L)).thenReturn(List.of(zone1));
        when(edgeNodeRepository.findByMonitoringZoneId(10L)).thenReturn(List.of(node1));
        when(edgeNodeRepository.findAll()).thenReturn(List.of(node1));

        FarmlandValidationResponse result = farmlandValidationService.validateFarmlandReadiness(1L);

        assertThat(result.isValid()).isTrue();
        assertThat(result.isReadyForMonitoring()).isTrue();
        assertThat(result.getOperationalStatus()).isEqualTo("MONITORING");
        assertThat(result.getActiveNodesCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should successfully create a monitoring zone for a field")
    void shouldCreateMonitoringZone() {
        Field field = Field.builder().id(1L).name("Test Farm").build();
        when(fieldRepository.findById(1L)).thenReturn(Optional.of(field));

        MonitoringZone savedZone = MonitoringZone.builder()
                .id(20L)
                .name("Section North")
                .code("SEC-N")
                .field(field)
                .areaType("SECTION")
                .cropType("Rice")
                .relativeX(0.0)
                .relativeY(0.0)
                .relativeWidth(1.0)
                .relativeHeight(0.5)
                .isArchived(false)
                .operationalStatus("READY")
                .build();

        when(monitoringZoneRepository.save(any(MonitoringZone.class))).thenReturn(savedZone);
        when(edgeNodeRepository.findByMonitoringZoneId(20L)).thenReturn(Collections.emptyList());

        MonitoringZoneRequest req = MonitoringZoneRequest.builder()
                .name("Section North")
                .code("SEC-N")
                .areaType("SECTION")
                .cropType("Rice")
                .relativeX(0.0)
                .relativeY(0.0)
                .relativeWidth(1.0)
                .relativeHeight(0.5)
                .build();

        MonitoringZoneResponse resp = zoneService.createMonitoringZone(1L, req);

        assertThat(resp).isNotNull();
        assertThat(resp.getId()).isEqualTo(20L);
        assertThat(resp.getName()).isEqualTo("Section North");
        assertThat(resp.getCode()).isEqualTo("SEC-N");
    }

    @Test
    @DisplayName("Should split a monitoring zone into two child sub-zones and archive the parent")
    void shouldSplitMonitoringZone() {
        Field field = Field.builder().id(1L).name("Test Farm").build();
        MonitoringZone parent = MonitoringZone.builder()
                .id(50L)
                .name("Full Range")
                .code("RNG-1")
                .field(field)
                .relativeX(0.0)
                .relativeY(0.0)
                .relativeWidth(1.0)
                .relativeHeight(1.0)
                .lengthMeters(100.0)
                .widthMeters(100.0)
                .areaSize(1.0)
                .isArchived(false)
                .build();

        when(monitoringZoneRepository.findById(50L)).thenReturn(Optional.of(parent));
        when(edgeNodeRepository.findByMonitoringZoneId(50L)).thenReturn(Collections.emptyList());

        when(monitoringZoneRepository.save(any(MonitoringZone.class))).thenAnswer(inv -> {
            MonitoringZone z = inv.getArgument(0);
            if (z.getId() == null) {
                z.setId(System.currentTimeMillis() % 10000 + (long) (Math.random() * 100));
            }
            return z;
        });

        SplitZoneRequest splitReq = SplitZoneRequest.builder()
                .orientation("VERTICAL")
                .ratio(0.5)
                .part1Name("West Half")
                .part1Code("RNG-1W")
                .part2Name("East Half")
                .part2Code("RNG-1E")
                .build();

        List<MonitoringZoneResponse> results = zoneService.splitMonitoringZone(50L, splitReq);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getName()).isEqualTo("West Half");
        assertThat(results.get(0).getRelativeWidth()).isEqualTo(0.5);
        assertThat(results.get(1).getName()).isEqualTo("East Half");
        assertThat(results.get(1).getRelativeX()).isEqualTo(0.5);
        assertThat(parent.getIsArchived()).isTrue();
    }
}

