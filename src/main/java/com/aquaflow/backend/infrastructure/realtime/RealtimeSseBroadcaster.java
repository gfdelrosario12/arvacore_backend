package com.aquaflow.backend.infrastructure.realtime;

import com.aquaflow.backend.dto.response.AlertResponse;
import com.aquaflow.backend.dto.response.NodeLatestTelemetryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class RealtimeSseBroadcaster {

    private final ObjectMapper objectMapper;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public RealtimeSseBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter subscribe(String clientId) {
        // 30 minute timeout with automatic ping/keep-alive
        SseEmitter emitter = new SseEmitter(1800000L);
        emitters.add(emitter);

        emitter.onCompletion(() -> {
            log.debug("SSE client completed: {}", clientId);
            emitters.remove(emitter);
        });
        emitter.onTimeout(() -> {
            log.debug("SSE client timeout: {}", clientId);
            emitters.remove(emitter);
        });
        emitter.onError((ex) -> {
            log.debug("SSE client error {}: {}", clientId, ex.getMessage());
            emitters.remove(emitter);
        });

        // Send initial heartbeat
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"status\":\"connected\",\"clientId\":\"" + clientId + "\"}"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    public void broadcastTelemetryUpdated(NodeLatestTelemetryResponse telemetry) {
        if (telemetry == null) return;
        broadcast("telemetry.updated", telemetry);
    }

    public void broadcastNotificationCreated(AlertResponse notification) {
        if (notification == null) return;
        broadcast("notification.created", notification);
    }

    private void broadcast(String eventName, Object data) {
        if (emitters.isEmpty()) return;

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("Failed to serialize SSE payload for {}", eventName, e);
            return;
        }

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(jsonPayload));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }
        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
        }
    }
}

