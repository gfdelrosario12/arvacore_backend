package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.NodePairingRequest;
import com.aquaflow.backend.entity.PairingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NodePairingRequestRepository extends JpaRepository<NodePairingRequest, Long> {
    List<NodePairingRequest> findByPairingStatusOrderByRequestedAtDesc(PairingStatus status);
    List<NodePairingRequest> findByNodeIdOrderByRequestedAtDesc(String nodeId);
    Optional<NodePairingRequest> findFirstByNodeIdAndPairingStatusOrderByRequestedAtDesc(String nodeId, PairingStatus status);
}

