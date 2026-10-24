package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.ArvaGateDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArvaGateDeviceRepository extends JpaRepository<ArvaGateDevice, String> {
    Optional<ArvaGateDevice> findByDeviceId(String deviceId);
}

