package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.ArvaGateCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArvaGateCommandRepository extends JpaRepository<ArvaGateCommand, String> {
    List<ArvaGateCommand> findByDeviceIdAndStatusOrderByCreatedAtAsc(String deviceId, String status);
    Optional<ArvaGateCommand> findByCommandId(String commandId);
}

