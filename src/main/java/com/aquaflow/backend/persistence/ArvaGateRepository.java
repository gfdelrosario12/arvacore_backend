package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.ArvaGate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArvaGateRepository extends JpaRepository<ArvaGate, Long> {
    List<ArvaGate> findByDeviceIdOrderByGateIndexAsc(String deviceId);
    Optional<ArvaGate> findByDeviceIdAndGateIndex(String deviceId, Integer gateIndex);
}

