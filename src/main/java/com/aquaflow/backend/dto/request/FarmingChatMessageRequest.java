package com.aquaflow.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmingChatMessageRequest {
    private String sessionId;
    
    @NotBlank(message = "Message text cannot be empty")
    private String message;
    
    private Long fieldId;
    private String selectedCrop;
    private String plantingMethod;
    private String intendedSchedule;
}

