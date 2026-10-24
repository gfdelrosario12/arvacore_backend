package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.PlantingTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlantingTaskRepository extends JpaRepository<PlantingTask, Long> {
    List<PlantingTask> findByPlantingPlanIdOrderByIdAsc(Long planId);
}

