package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.TelemetryStreamEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TelemetryStreamEventRepository extends JpaRepository<TelemetryStreamEvent, Long>, JpaSpecificationExecutor<TelemetryStreamEvent> {

    Optional<TelemetryStreamEvent> findByEventId(String eventId);

    List<TelemetryStreamEvent> findTop50ByOrderByReceivedAtDesc();

    List<TelemetryStreamEvent> findTop100ByOrderByReceivedAtDesc();

    List<TelemetryStreamEvent> findByReceivedAtAfterOrderByReceivedAtDesc(LocalDateTime after);

    Page<TelemetryStreamEvent> findByNodeId(String nodeId, Pageable pageable);

    Page<TelemetryStreamEvent> findByFieldId(Long fieldId, Pageable pageable);

    Page<TelemetryStreamEvent> findByStatus(String status, Pageable pageable);

    long countByReceivedAtAfter(LocalDateTime since);

    @Query("SELECT COUNT(DISTINCT e.nodeId) FROM TelemetryStreamEvent e")
    long countDistinctNodeId();

    @Query("SELECT COUNT(e) FROM TelemetryStreamEvent e WHERE e.status IN ('FAILED', 'REJECTED')")
    long countFailedOrRejected();

    Optional<TelemetryStreamEvent> findFirstByOrderByReceivedAtDesc();

    @Query("SELECT DISTINCT e.nodeId FROM TelemetryStreamEvent e WHERE e.nodeId IS NOT NULL ORDER BY e.nodeId ASC")
    List<String> findDistinctNodeIds();
}

