package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.MonitoringZone;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonitoringZoneRepository extends JpaRepository<MonitoringZone, Long> {
    List<MonitoringZone> findByFieldId(Long fieldId);
    List<MonitoringZone> findByFieldIdAndIsArchivedFalse(Long fieldId);
    Page<MonitoringZone> findByFieldId(Long fieldId, Pageable pageable);
    Optional<MonitoringZone> findByName(String name);
    Optional<MonitoringZone> findByFieldIdAndCode(Long fieldId, String code);
    List<MonitoringZone> findByIsArchivedFalse();
}

