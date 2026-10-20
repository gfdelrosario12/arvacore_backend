package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.DeviceCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeviceCredentialRepository extends JpaRepository<DeviceCredential, Long> {
    Optional<DeviceCredential> findByNodeId(String nodeId);
    Optional<DeviceCredential> findByBootstrapKeyId(String bootstrapKeyId);
    Optional<DeviceCredential> findByOperationalKeyId(String operationalKeyId);
}
