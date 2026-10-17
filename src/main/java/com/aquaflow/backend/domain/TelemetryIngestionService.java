package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.TelemetryBatchUplinkRequest;
import com.aquaflow.backend.dto.request.TelemetryUplinkRequest;
import com.aquaflow.backend.dto.response.TelemetryBatchUplinkResponse;
import com.aquaflow.backend.dto.response.TelemetryUplinkResponse;

public interface TelemetryIngestionService {

    TelemetryUplinkResponse ingestUplink(TelemetryUplinkRequest request);

    TelemetryUplinkResponse ingestUplinkForNode(String nodeId, TelemetryUplinkRequest request);

    TelemetryBatchUplinkResponse ingestBatchUplink(TelemetryBatchUplinkRequest request);
    
    void clearNodeCache(String nodeId);
}

