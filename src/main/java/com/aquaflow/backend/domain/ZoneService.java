package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.UpdateAwdProfileRequest;
import com.aquaflow.backend.dto.request.UpdateCropStageRequest;
import com.aquaflow.backend.dto.request.ZoneRequest;
import com.aquaflow.backend.dto.response.MonitoringZoneResponse;
import com.aquaflow.backend.dto.response.ZoneResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ZoneService {
    ZoneResponse createZone(ZoneRequest request);
    ZoneResponse getZoneById(Long id);
    MonitoringZoneResponse getMonitoringZoneById(Long id);
    Page<ZoneResponse> getAllZones(Pageable pageable);
    ZoneResponse updateZone(Long id, ZoneRequest request);
    MonitoringZoneResponse updateCropStage(Long id, UpdateCropStageRequest request);
    MonitoringZoneResponse updateAwdProfile(Long id, UpdateAwdProfileRequest request);
    void deleteZone(Long id);
    List<ZoneResponse> getZonesByCropType(String cropType);
    List<ZoneResponse> getAllZones();

    List<MonitoringZoneResponse> getMonitoringZonesByFieldId(Long fieldId);
    MonitoringZoneResponse createMonitoringZone(Long fieldId, com.aquaflow.backend.dto.request.MonitoringZoneRequest request);
    MonitoringZoneResponse updateMonitoringZone(Long zoneId, com.aquaflow.backend.dto.request.MonitoringZoneRequest request);
    void archiveMonitoringZone(Long zoneId);
    List<MonitoringZoneResponse> splitMonitoringZone(Long zoneId, com.aquaflow.backend.dto.request.SplitZoneRequest request);
    MonitoringZoneResponse mergeMonitoringZones(Long fieldId, com.aquaflow.backend.dto.request.MergeZonesRequest request);
    com.aquaflow.backend.dto.response.FieldResponse updateFarmlandLayout(Long fieldId, com.aquaflow.backend.dto.request.FarmlandLayoutDto layout);
    List<com.aquaflow.backend.dto.response.NodeAssignmentHistoryResponse> getFieldAssignmentHistory(Long fieldId);
}