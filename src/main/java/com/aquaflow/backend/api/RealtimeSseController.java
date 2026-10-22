package com.aquaflow.backend.api;

import com.aquaflow.backend.infrastructure.realtime.RealtimeSseBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

    @GetMapping(
            value = "/sse",
            produces = {MediaType.TEXT_EVENT_STREAM_VALUE, MediaType.ALL_VALUE}
    )
    public ResponseEntity<SseEmitter> subscribe(
            @RequestParam(required = false) String clientId,
            Principal principal
    ) {
        String effectiveId = clientId != null && !clientId.isBlank()
                ? clientId
                : (principal != null ? principal.getName() : "anon-" + UUID.randomUUID().toString().substring(0, 8));

        log.info("SSE subscription request from: {}", effectiveId);
        SseEmitter emitter = sseBroadcaster.subscribe(effectiveId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_EVENT_STREAM);
        headers.setCacheControl("no-cache, no-transform");
        headers.set("X-Accel-Buffering", "no");
        headers.setConnection("keep-alive");

        return new ResponseEntity<>(emitter, headers, HttpStatus.OK);
    }
}
