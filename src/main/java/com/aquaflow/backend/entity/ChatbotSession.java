package com.aquaflow.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "chatbot_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatbotSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true, length = 64)
    private String sessionId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "field_id")
    private Long fieldId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "current_step", nullable = false, length = 64)
    private String currentStep; // CROP_SELECTION, FIELD_AND_METHOD, READINESS_ASSESSMENT, CONFIRMATION_PENDING, PREPARATION_ACTIVE, COMPLETED

    @Column(name = "selected_crop", length = 100)
    private String selectedCrop;

    @Column(name = "planting_method", length = 100)
    private String plantingMethod;

    @Column(name = "intended_schedule", length = 255)
    private String intendedSchedule;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;
}

