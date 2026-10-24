package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.ArvaGateEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArvaGateEventRepository extends JpaRepository<ArvaGateEvent, Long> {
    List<ArvaGateEvent> findTop50ByDeviceIdOrderByCreatedAtDesc(String deviceId);
}

