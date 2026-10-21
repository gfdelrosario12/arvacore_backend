package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.response.TelemetryStreamEventResponse;
import com.aquaflow.backend.dto.response.TelemetryStreamSummaryResponse;
import com.aquaflow.backend.entity.TelemetryStreamEvent;
import com.aquaflow.backend.infrastructure.event.SystemEventPublisher;
import com.aquaflow.backend.persistence.TelemetryStreamEventRepository;
import com.aquaflow.backend.persistence.ZoneRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelemetryStreamServiceImpl implements TelemetryStreamService {

    private final TelemetryStreamEventRepository telemetryStreamEventRepository;
    private final SystemEventPublisher systemEventPublisher;
    private final ZoneRepository zoneRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TelemetryStreamEvent recordStreamEvent(TelemetryStreamEvent event) {
        if (event == null) return null;
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getReceivedAt() == null) {
            event.setReceivedAt(LocalDateTime.now());
        }

        TelemetryStreamEvent saved = telemetryStreamEventRepository.save(event);
        log.info("Persisted telemetry stream event {} for node {} (status: {})",
                saved.getEventId(), saved.getNodeId(), saved.getStatus());

        try {
            systemEventPublisher.publishTelemetryStreamEvent(saved);
        } catch (Exception e) {
            log.error("Failed to broadcast telemetry stream event {}: {}", saved.getEventId(), e.getMessage());
        }

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TelemetryStreamEventResponse> getEvents(
            String nodeId,
            Long fieldId,
            String status,
            LocalDateTime from,
            LocalDateTime to,
            String search,
            Pageable pageable
    ) {
        Specification<TelemetryStreamEvent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (nodeId != null && !nodeId.isBlank()) {
                predicates.add(cb.equal(root.get("nodeId"), nodeId.trim()));
            }
            if (fieldId != null) {
                predicates.add(cb.equal(root.get("fieldId"), fieldId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("status")), status.trim().toUpperCase()));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("receivedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("receivedAt"), to));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate nodeSearch = cb.like(cb.lower(root.get("nodeId")), pattern);
                Predicate statusSearch = cb.like(cb.lower(root.get("status")), pattern);
                Predicate msgSearch = cb.like(cb.lower(root.get("statusMessage")), pattern);
                Predicate zoneSearch = cb.like(cb.lower(root.get("zoneCode")), pattern);
                predicates.add(cb.or(nodeSearch, statusSearch, msgSearch, zoneSearch));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // Ensure descending sort by receivedAt if not specified
        Pageable effectivePageable = pageable;
        if (pageable.getSort().isUnsorted()) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "receivedAt"));
        }

        return telemetryStreamEventRepository.findAll(spec, effectivePageable)
                .map(TelemetryStreamEventResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TelemetryStreamEventResponse> getRecentEvents(int limit, LocalDateTime after) {
        int boundedLimit = Math.min(Math.max(limit, 1), 200);
        if (after != null) {
            return telemetryStreamEventRepository.findByReceivedAtAfterOrderByReceivedAtDesc(after)
                    .stream()
                    .limit(boundedLimit)
                    .map(TelemetryStreamEventResponse::fromEntity)
                    .collect(Collectors.toList());
        }

        Pageable pageable = PageRequest.of(0, boundedLimit, Sort.by(Sort.Direction.DESC, "receivedAt"));
        return telemetryStreamEventRepository.findAll(pageable)
                .map(TelemetryStreamEventResponse::fromEntity)
                .getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public TelemetryStreamSummaryResponse getSummary() {
        long total = telemetryStreamEventRepository.count();
        long recent = telemetryStreamEventRepository.countByReceivedAtAfter(LocalDateTime.now().minusHours(24));
        long distinctNodes = telemetryStreamEventRepository.countDistinctNodeId();
        long failedOrRejected = telemetryStreamEventRepository.countFailedOrRejected();
        Optional<TelemetryStreamEvent> latest = telemetryStreamEventRepository.findFirstByOrderByReceivedAtDesc();

        return TelemetryStreamSummaryResponse.builder()
                .totalTransmissions(total)
                .recentTransmissionsCount(recent)
                .distinctNodesCount(distinctNodes)
                .failedOrRejectedCount(failedOrRejected)
                .lastEventReceivedAt(latest.map(TelemetryStreamEvent::getReceivedAt).orElse(null))
                .activeConnectionsCount((int) distinctNodes)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getFilterOptions() {
        List<String> distinctNodeIds = telemetryStreamEventRepository.findDistinctNodeIds();
        List<String> statuses = List.of("PROCESSED", "PARTIALLY_PROCESSED", "REJECTED", "FAILED");
        List<Map<String, Object>> zones = zoneRepository.findAll().stream()
                .map(z -> Map.<String, Object>of(
                        "id", z.getId(),
                        "name", z.getName()
                ))
                .collect(Collectors.toList());

        return Map.of(
                "nodeIds", distinctNodeIds,
                "statuses", statuses,
                "zones", zones
        );
    }
}
