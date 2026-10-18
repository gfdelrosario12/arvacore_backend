package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.NodeAssignmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NodeAssignmentHistoryRepository extends JpaRepository<NodeAssignmentHistory, Long> {

    List<NodeAssignmentHistory> findByEdgeNodeIdOrderByAssignedAtDesc(Long edgeNodeId);

    List<NodeAssignmentHistory> findByFieldIdOrderByAssignedAtDesc(Long fieldId);

    List<NodeAssignmentHistory> findByMonitoringZoneIdOrderByAssignedAtDesc(Long monitoringZoneId);

    @Query("SELECT h FROM NodeAssignmentHistory h WHERE h.edgeNodeId = :edgeNodeId AND h.unassignedAt IS NULL")
    Optional<NodeAssignmentHistory> findActiveAssignment(@Param("edgeNodeId") Long edgeNodeId);
}

