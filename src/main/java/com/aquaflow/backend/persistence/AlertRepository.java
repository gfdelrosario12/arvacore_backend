package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long>, JpaSpecificationExecutor<Alert> {
    List<Alert> findByZoneId(Long zoneId);
    Page<Alert> findByZoneId(Long zoneId, Pageable pageable);
    List<Alert> findByDeviceId(String deviceId);
    Page<Alert> findByDeviceId(String deviceId, Pageable pageable);
    List<Alert> findByAcknowledgedFalse();
    List<Alert> findByAcknowledgedFalseOrderByCreatedAtDesc();
    List<Alert> findAllByOrderByCreatedAtDesc();
    List<Alert> findByIdGreaterThanOrderByCreatedAtDesc(Long sinceId);
    List<Alert> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);
    Optional<Alert> findFirstByDedupKeyAndCreatedAtAfterOrderByCreatedAtDesc(String dedupKey, LocalDateTime after);
    Optional<Alert> findFirstByOrderByCreatedAtDesc();

    long countByAcknowledgedFalse();

    @Modifying
    @Query("UPDATE Alert a SET a.acknowledged = true, a.acknowledgedAt = :now WHERE a.acknowledged = false")
    int markAllAsRead(@Param("now") LocalDateTime now);
}
