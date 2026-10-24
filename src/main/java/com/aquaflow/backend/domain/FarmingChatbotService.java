package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.FarmingChatMessageRequest;
import com.aquaflow.backend.dto.response.FarmingChatResponse;
import com.aquaflow.backend.entity.PlantingPlan;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface FarmingChatbotService {
    FarmingChatResponse processMessage(FarmingChatMessageRequest request, Authentication authentication);
    FarmingChatResponse getSessionState(String sessionId, Authentication authentication);
    FarmingChatResponse resetSession(String sessionId, Long fieldId, Authentication authentication);
    PlantingPlan confirmPlan(Long planId, Authentication authentication);
    FarmingChatResponse toggleTask(Long planId, Long taskId, Authentication authentication);
    List<PlantingPlan> getPlansForField(Long fieldId, Authentication authentication);
}


