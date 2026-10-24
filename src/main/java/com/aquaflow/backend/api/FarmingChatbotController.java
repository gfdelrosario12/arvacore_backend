package com.aquaflow.backend.api;

import com.aquaflow.backend.domain.FarmingChatbotService;
import com.aquaflow.backend.dto.request.FarmingChatMessageRequest;
import com.aquaflow.backend.dto.response.FarmingChatResponse;
import com.aquaflow.backend.entity.PlantingPlan;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/farming-chat")
@RequiredArgsConstructor
@Slf4j
public class FarmingChatbotController {

    private final FarmingChatbotService chatbotService;

    @PostMapping("/message")
    public ResponseEntity<FarmingChatResponse> sendMessage(
            @Valid @RequestBody FarmingChatMessageRequest request,
            Authentication authentication) {
        log.info("POST /api/v1/farming-chat/message by authenticated user {}", authentication != null ? authentication.getName() : "ANONYMOUS");
        return ResponseEntity.ok(chatbotService.processMessage(request, authentication));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<FarmingChatResponse> getSession(
            @PathVariable String sessionId,
            Authentication authentication) {
        return ResponseEntity.ok(chatbotService.getSessionState(sessionId, authentication));
    }

    @PostMapping("/sessions/{sessionId}/reset")
    public ResponseEntity<FarmingChatResponse> resetSession(
            @PathVariable String sessionId,
            @RequestParam(required = false, defaultValue = "1") Long fieldId,
            Authentication authentication) {
        return ResponseEntity.ok(chatbotService.resetSession(sessionId, fieldId, authentication));
    }

    @PostMapping("/plans/{planId}/confirm")
    public ResponseEntity<PlantingPlan> confirmPlan(
            @PathVariable Long planId,
            Authentication authentication) {
        log.info("POST /api/v1/farming-chat/plans/{}/confirm by {}", planId, authentication != null ? authentication.getName() : "ANONYMOUS");
        return ResponseEntity.ok(chatbotService.confirmPlan(planId, authentication));
    }

    @PostMapping("/plans/{planId}/tasks/{taskId}/toggle")
    public ResponseEntity<FarmingChatResponse> toggleTask(
            @PathVariable Long planId,
            @PathVariable Long taskId,
            Authentication authentication) {
        return ResponseEntity.ok(chatbotService.toggleTask(planId, taskId, authentication));
    }

    @GetMapping("/fields/{fieldId}/plans")
    public ResponseEntity<List<PlantingPlan>> getPlansForField(
            @PathVariable Long fieldId,
            Authentication authentication) {
        return ResponseEntity.ok(chatbotService.getPlansForField(fieldId, authentication));
    }
}
