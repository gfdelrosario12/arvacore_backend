package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.PlantingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface PlantingPlanRepository extends JpaRepository<PlantingPlan, Long> {
    Optional<PlantingPlan> findByPlanCode(String planCode);
    Optional<PlantingPlan> findTopBySessionIdOrderByUpdatedAtDesc(String sessionId);
    List<PlantingPlan> findByFieldIdOrderByCreatedAtDesc(Long fieldId);
}

