package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.response.TelemetryStreamEventResponse;
import com.aquaflow.backend.dto.response.TelemetryStreamSummaryResponse;
import com.aquaflow.backend.entity.TelemetryStreamEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface TelemetryStreamService {

    TelemetryStreamEvent recordStreamEvent(TelemetryStreamEvent event);

    Page<TelemetryStreamEventResponse> getEvents(
            String nodeId,
            Long fieldId,
            String status,
            LocalDateTime from,
            LocalDateTime to,
            String search,
            Pageable pageable
    );

    List<TelemetryStreamEventResponse> getRecentEvents(int limit, LocalDateTime after);

    TelemetryStreamSummaryResponse getSummary();

    Map<String, Object> getFilterOptions();
}

