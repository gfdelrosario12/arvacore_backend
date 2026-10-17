package com.aquaflow.backend.api;

import com.aquaflow.backend.domain.TelemetryStreamService;
import com.aquaflow.backend.dto.response.TelemetryStreamEventResponse;
import com.aquaflow.backend.dto.response.TelemetryStreamSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/telemetry/stream")
@RequiredArgsConstructor
@Slf4j
public class TelemetryStreamController {

    private final TelemetryStreamService telemetryStreamService;

    @GetMapping("/events")
    public ResponseEntity<Page<TelemetryStreamEventResponse>> getEvents(
            @RequestParam(required = false) String nodeId,
            @RequestParam(required = false) Long fieldId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String search,
            Pageable pageable
    ) {
        log.debug("GET /api/v1/telemetry/stream/events (nodeId={}, fieldId={}, status={}, search={})",
                nodeId, fieldId, status, search);
        return ResponseEntity.ok(telemetryStreamService.getEvents(nodeId, fieldId, status, from, to, search, pageable));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<TelemetryStreamEventResponse>> getRecentEvents(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime after
    ) {
        log.debug("GET /api/v1/telemetry/stream/recent (limit={}, after={})", limit, after);
        return ResponseEntity.ok(telemetryStreamService.getRecentEvents(limit, after));
    }

    @GetMapping("/summary")
    public ResponseEntity<TelemetryStreamSummaryResponse> getSummary() {
        log.debug("GET /api/v1/telemetry/stream/summary");
        return ResponseEntity.ok(telemetryStreamService.getSummary());
    }

    @GetMapping("/filters")
    public ResponseEntity<Map<String, Object>> getFilterOptions() {
        log.debug("GET /api/v1/telemetry/stream/filters");
        return ResponseEntity.ok(telemetryStreamService.getFilterOptions());
    }
}

