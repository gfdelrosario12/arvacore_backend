package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.SystemState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemStateRepository extends JpaRepository<SystemState, String> {
}

