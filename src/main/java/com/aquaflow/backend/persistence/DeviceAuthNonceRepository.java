package com.aquaflow.backend.persistence;

import com.aquaflow.backend.entity.DeviceAuthNonce;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface DeviceAuthNonceRepository extends JpaRepository<DeviceAuthNonce, Long> {
    Optional<DeviceAuthNonce> findByNodeIdAndNonce(String nodeId, String nonce);

    @Modifying
    @Query("DELETE FROM DeviceAuthNonce n WHERE n.expiresAt < :now")
    void deleteExpiredNonces(@Param("now") LocalDateTime now);
}

