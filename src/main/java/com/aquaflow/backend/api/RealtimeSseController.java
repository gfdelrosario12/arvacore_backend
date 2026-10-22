package com.aquaflow.backend.api;

import com.aquaflow.backend.infrastructure.realtime.RealtimeSseBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/realtime", "/api/realtime"})
@RequiredArgsConstructor
@Slf4j
public class RealtimeSseController {

    private final RealtimeSseBroadcaster sseBroadcaster;

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @RequestParam(required = false) String clientId,
            Principal principal
    ) {
        String effectiveId = clientId != null && !clientId.isBlank()
                ? clientId
                : (principal != null ? principal.getName() : "anon-" + UUID.randomUUID().toString().substring(0, 8));

        log.info("SSE subscription request from: {}", effectiveId);
        return sseBroadcaster.subscribe(effectiveId);
    }
}

