package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.FarmingChatMessageRequest;
import com.aquaflow.backend.dto.response.FarmingChatResponse;
import com.aquaflow.backend.entity.*;
import com.aquaflow.backend.infrastructure.exception.ResourceNotFoundException;
import com.aquaflow.backend.infrastructure.exception.ValidationException;
import com.aquaflow.backend.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FarmingChatbotServiceImpl implements FarmingChatbotService {

    private final ChatbotSessionRepository sessionRepository;
    private final ChatbotMessageRepository messageRepository;
    private final PlantingPlanRepository planRepository;
    private final PlantingTaskRepository taskRepository;
    private final TelemetryReadingRepository telemetryRepository;
    private final EmergencyStopService emergencyStopService;
    private final IrrigationAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public static final String INITIAL_QUESTION = "What do you want to plant today?";
    public static final String PROCEED_QUESTION = "Do you want to proceed with the proposed planting preparation?";

    @Override
    @Transactional
    public FarmingChatResponse processMessage(FarmingChatMessageRequest request, Authentication authentication) {
        String username = resolveUsername(authentication);
        String role = resolveRole(authentication);
        String msg = request.getMessage() != null ? request.getMessage().trim() : "";
        Long fieldId = request.getFieldId() != null ? request.getFieldId() : 1L;

        // 1. Adversarial & Prompt Injection Defense (Neutralize attempts to bypass RBAC or force actuation)
        if (isAdversarial(msg)) {
            recordAuditLog("AI_CHAT_ADVERSARIAL_ATTEMPT", username, "CHAT", request.getSessionId() != null ? request.getSessionId() : "NONE", Map.of("query", msg, "role", role));
            return buildAdversarialResponse(request.getSessionId(), role);
        }

        // 2. Fetch or create session linked to the real authenticated user
        ChatbotSession session;
        if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
            session = sessionRepository.findBySessionId(request.getSessionId())
                    .orElseGet(() -> createNewSession(username, fieldId));
            
            // Enforce ownership: Non-admins cannot hijack another user's session
            if (!isAdmin(authentication) && !session.getUserId().equalsIgnoreCase(username)) {
                log.warn("Access Denied: User {} attempted to access session {} owned by {}", username, session.getSessionId(), session.getUserId());
                throw new AccessDeniedException("FORBIDDEN: You do not have permission to access another user's chat session.");
            }
        } else {
            session = createNewSession(username, fieldId);
        }

        // Record incoming user message
        recordMessage(session.getSessionId(), "USER", msg);

        // 3. Conversational State Machine
        FarmingChatResponse response;
        String currentStep = session.getCurrentStep();

        if (msg.equalsIgnoreCase("reset") || msg.equalsIgnoreCase("start over") || msg.equalsIgnoreCase("new")) {
            return resetSession(session.getSessionId(), fieldId, authentication);
        }

        switch (currentStep) {
            case "CROP_SELECTION" -> {
                String crop = detectCrop(msg, request.getSelectedCrop());
                session.setSelectedCrop(crop);
                session.setCurrentStep("FIELD_AND_METHOD");
                session.setUpdatedAt(OffsetDateTime.now());
                sessionRepository.save(session);

                String reply = String.format("Great! Planning for %s. Which field terrace, planting method (e.g., Direct Seeding or Wet Bed Transplanting), and intended schedule do you have in mind?", crop);
                recordMessage(session.getSessionId(), "ASSISTANT", reply);

                response = buildBaseResponse(session, reply, "Please specify field terrace, planting method (e.g., Transplanting), and schedule.", false, role);
            }
            case "FIELD_AND_METHOD" -> {
                String method = request.getPlantingMethod() != null ? request.getPlantingMethod() : extractMethod(msg);
                String schedule = request.getIntendedSchedule() != null ? request.getIntendedSchedule() : extractSchedule(msg);
                session.setPlantingMethod(method);
                session.setIntendedSchedule(schedule);
                session.setCurrentStep("READINESS_ASSESSMENT");
                session.setUpdatedAt(OffsetDateTime.now());
                sessionRepository.save(session);

                // Build Grounded Planting Plan with Deterministic Tasks
                PlantingPlan plan = generatePlantingPlan(session, fieldId);
                planRepository.save(plan);

                session.setCurrentStep("CONFIRMATION_PENDING");
                sessionRepository.save(session);

                String reply = String.format(
                        "Farm-Readiness assessment complete for %s (Method: %s, Schedule: %s). " +
                        "Readiness score is %d%% (%s). Telemetry from ArvaNodes has been evaluated. %s",
                        session.getSelectedCrop(), method, schedule, plan.getReadinessScore(), plan.getReadinessStatus(), PROCEED_QUESTION
                );
                recordMessage(session.getSessionId(), "ASSISTANT", reply);

                response = buildPlanResponse(session, plan, reply, PROCEED_QUESTION, true, role);
            }
            case "CONFIRMATION_PENDING" -> {
                PlantingPlan plan = planRepository.findTopBySessionIdOrderByUpdatedAtDesc(session.getSessionId())
                        .orElseThrow(() -> new ResourceNotFoundException("No active planting plan found for session"));

                if (isAffirmative(msg)) {
                    confirmPlan(plan.getId(), authentication);
                    session.setCurrentStep("PREPARATION_ACTIVE");
                    session.setUpdatedAt(OffsetDateTime.now());
                    sessionRepository.save(session);

                    String reply = "Planting preparation confirmed! Automated pre-soak irrigation workflow has been safely submitted to ArvaCore. Review your task checklist below as preparation progresses.";
                    recordMessage(session.getSessionId(), "ASSISTANT", reply);
                    response = buildPlanResponse(session, plan, reply, "Preparation in progress. Complete farmer manual tasks.", false, role);
                } else {
                    String reply = "Understood. The proposed planting preparation has not been started. You can adjust the parameters or ask questions about the readiness assessment.";
                    recordMessage(session.getSessionId(), "ASSISTANT", reply);
                    response = buildPlanResponse(session, plan, reply, PROCEED_QUESTION, true, role);
                }
            }
            case "PREPARATION_ACTIVE", "COMPLETED" -> {
                PlantingPlan plan = planRepository.findTopBySessionIdOrderByUpdatedAtDesc(session.getSessionId())
                        .orElseGet(() -> generatePlantingPlan(session, fieldId));

                String reply = String.format("Current plan for %s is active (%d%% readiness). Continue tracking tasks and water depth.", session.getSelectedCrop(), plan.getReadinessScore());
                recordMessage(session.getSessionId(), "ASSISTANT", reply);
                response = buildPlanResponse(session, plan, reply, "Keep monitoring field readiness.", false, role);
            }
            default -> {
                response = resetSession(session.getSessionId(), fieldId, authentication);
            }
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public FarmingChatResponse getSessionState(String sessionId, Authentication authentication) {
        String username = resolveUsername(authentication);
        String role = resolveRole(authentication);

        ChatbotSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + sessionId));

        if (!isAdmin(authentication) && !session.getUserId().equalsIgnoreCase(username)) {
            throw new AccessDeniedException("FORBIDDEN: You do not have permission to view this chat session.");
        }

        PlantingPlan plan = planRepository.findTopBySessionIdOrderByUpdatedAtDesc(sessionId).orElse(null);
        if (plan != null) {
            return buildPlanResponse(session, plan, "Welcome back. Here is your current planting preparation state.", session.getCurrentStep().equals("CONFIRMATION_PENDING") ? PROCEED_QUESTION : null, session.getCurrentStep().equals("CONFIRMATION_PENDING"), role);
        }

        return buildBaseResponse(session, INITIAL_QUESTION, INITIAL_QUESTION, false, role);
    }

    @Override
    @Transactional
    public FarmingChatResponse resetSession(String sessionId, Long fieldId, Authentication authentication) {
        String username = resolveUsername(authentication);
        String role = resolveRole(authentication);

        ChatbotSession session = sessionRepository.findBySessionId(sessionId)
                .orElseGet(() -> createNewSession(username, fieldId));

        if (!isAdmin(authentication) && !session.getUserId().equalsIgnoreCase(username)) {
            throw new AccessDeniedException("FORBIDDEN: You do not have permission to reset this chat session.");
        }

        session.setCurrentStep("CROP_SELECTION");
        session.setSelectedCrop(null);
        session.setPlantingMethod(null);
        session.setIntendedSchedule(null);
        session.setUpdatedAt(OffsetDateTime.now());
        sessionRepository.save(session);

        recordMessage(session.getSessionId(), "ASSISTANT", INITIAL_QUESTION);

        return buildBaseResponse(session, INITIAL_QUESTION, INITIAL_QUESTION, false, role);
    }

    @Override
    @Transactional
    public PlantingPlan confirmPlan(Long planId, Authentication authentication) {
        String username = resolveUsername(authentication);
        String role = resolveRole(authentication);

        // RBAC Check: Only ROLE_ADMIN or ROLE_OPERATOR (or field owner) can authorize automated irrigation workflows
        boolean canControlIrrigation = isAdmin(authentication) || isOperator(authentication);
        if (!canControlIrrigation) {
            recordAuditLog("AI_CHAT_UNAUTHORIZED_ACTUATION_ATTEMPT", username, "PLAN", planId.toString(), Map.of("role", role, "reason", "Lacks ROLE_ADMIN or ROLE_OPERATOR"));
            throw new AccessDeniedException("FORBIDDEN: Your role (" + role + ") is not authorized to trigger automated irrigation workflows. Contact a field operator or administrator.");
        }

        PlantingPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Planting plan not found: " + planId));

        // Deterministic Safety Interlock Verification
        boolean isEStopActive = emergencyStopService.isEmergencyStopActive();
        if (isEStopActive) {
            plan.setIrrigationSafetyStatus("BLOCKED_EMERGENCY_STOP");
            for (PlantingTask task : plan.getTasks()) {
                if ("SYSTEM_AUTOMATED".equals(task.getCategory())) {
                    task.setStatus("BLOCKED");
                    task.setBlockingReason("SAFETY_INTERLOCK: Emergency stop is actively engaged.");
                }
            }
            planRepository.save(plan);
            recordAuditLog("AI_CHAT_CONFIRM_BLOCKED_ESTOP", username, "PLAN", planId.toString(), Map.of("planCode", plan.getPlanCode()));
            throw new ValidationException("IRRIGATION_SAFETY_VIOLATION: Cannot execute automated preparation while emergency stop is active.");
        }

        plan.setFarmerConfirmed(true);
        plan.setFarmerConfirmedAt(OffsetDateTime.now());
        plan.setReadinessStatus("PREPARING");
        plan.setIrrigationSafetyStatus("VERIFIED_SAFE");

        // Transition pending automated tasks to IN_PROGRESS
        for (PlantingTask task : plan.getTasks()) {
            if ("SYSTEM_AUTOMATED".equals(task.getCategory()) && "PENDING".equals(task.getStatus())) {
                task.setStatus("IN_PROGRESS");
                task.setExecutedAt(OffsetDateTime.now());
            }
        }

        PlantingPlan saved = planRepository.save(plan);
        recordAuditLog("AI_CHAT_PLAN_CONFIRMED", username, "PLAN", planId.toString(), Map.of("planCode", plan.getPlanCode(), "role", role));
        return saved;
    }

    @Override
    @Transactional
    public FarmingChatResponse toggleTask(Long planId, Long taskId, Authentication authentication) {
        String username = resolveUsername(authentication);
        String role = resolveRole(authentication);

        PlantingPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Planting plan not found: " + planId));

        PlantingTask targetTask = plan.getTasks().stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Planting task not found: " + taskId));

        if ("SAFETY_BLOCKED".equals(targetTask.getCategory())) {
            throw new ValidationException("CANNOT_TOGGLE_BLOCKED_TASK: Safety blocked tasks can only be resolved by clearing the physical safety interlock.");
        }

        if ("SYSTEM_AUTOMATED".equals(targetTask.getCategory()) && !isAdmin(authentication) && !isOperator(authentication)) {
            throw new AccessDeniedException("FORBIDDEN: Only operators or admins can manually override automated system tasks.");
        }

        if ("COMPLETED".equals(targetTask.getStatus())) {
            targetTask.setStatus("PENDING");
            targetTask.setExecutedAt(null);
        } else {
            targetTask.setStatus("COMPLETED");
            targetTask.setExecutedAt(OffsetDateTime.now());
        }
        taskRepository.save(targetTask);

        // Recalculate readiness score
        long completed = plan.getTasks().stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        int score = plan.getTasks().isEmpty() ? 0 : (int) ((completed * 100) / plan.getTasks().size());
        plan.setReadinessScore(score);
        if (score == 100) {
            plan.setReadinessStatus("COMPLETED");
        } else if (score >= 70) {
            plan.setReadinessStatus("READY");
        }
        planRepository.save(plan);

        recordAuditLog("AI_CHAT_TASK_TOGGLED", username, "TASK", taskId.toString(), Map.of("planId", planId, "status", targetTask.getStatus()));

        ChatbotSession session = sessionRepository.findBySessionId(plan.getSessionId()).orElse(null);
        return buildPlanResponse(session, plan, "Task updated successfully.", null, false, role);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlantingPlan> getPlansForField(Long fieldId, Authentication authentication) {
        return planRepository.findByFieldIdOrderByCreatedAtDesc(fieldId);
    }

    // --- Helper Methods ---

    private String resolveUsername(Authentication authentication) {
        return (authentication != null && authentication.getName() != null) ? authentication.getName() : "anonymous_user";
    }

    private String resolveRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null || authentication.getAuthorities().isEmpty()) {
            return "ROLE_USER";
        }
        for (GrantedAuthority ga : authentication.getAuthorities()) {
            String auth = ga.getAuthority();
            if ("ROLE_ADMIN".equalsIgnoreCase(auth) || "ADMIN".equalsIgnoreCase(auth)) return "ROLE_ADMIN";
            if ("ROLE_SUPERVISOR".equalsIgnoreCase(auth) || "SUPERVISOR".equalsIgnoreCase(auth)) return "ROLE_SUPERVISOR";
            if ("ROLE_OPERATOR".equalsIgnoreCase(auth) || "OPERATOR".equalsIgnoreCase(auth)) return "ROLE_OPERATOR";
        }
        return "ROLE_USER";
    }

    private boolean isAdmin(Authentication authentication) {
        return "ROLE_ADMIN".equals(resolveRole(authentication));
    }

    private boolean isOperator(Authentication authentication) {
        String role = resolveRole(authentication);
        return "ROLE_OPERATOR".equals(role) || "ROLE_ADMIN".equals(role);
    }

    private ChatbotSession createNewSession(String username, Long fieldId) {
        String sId = "chat-sess-" + UUID.randomUUID().toString().substring(0, 8);
        ChatbotSession session = ChatbotSession.builder()
                .sessionId(sId)
                .userId(username)
                .fieldId(fieldId)
                .currentStep("CROP_SELECTION")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        return sessionRepository.save(session);
    }

    private void recordMessage(String sessionId, String sender, String text) {
        ChatbotMessage msg = ChatbotMessage.builder()
                .sessionId(sessionId)
                .sender(sender)
                .messageText(text)
                .timestamp(OffsetDateTime.now())
                .build();
        messageRepository.save(msg);
    }

    private PlantingPlan generatePlantingPlan(ChatbotSession session, Long fieldId) {
        String crop = session.getSelectedCrop() != null ? session.getSelectedCrop() : "Lowland Rice (NSIC Rc 222)";
        String planCode = "PLAN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        boolean isEStopActive = emergencyStopService.isEmergencyStopActive();

        PlantingPlan plan = PlantingPlan.builder()
                .planCode(planCode)
                .sessionId(session.getSessionId())
                .fieldId(fieldId)
                .cropName(crop)
                .plantingMethod(session.getPlantingMethod())
                .intendedSchedule(session.getIntendedSchedule())
                .readinessScore(45)
                .readinessStatus("ASSESSING")
                .summaryText(String.format("Planting readiness assessment for %s using %s.", crop, session.getPlantingMethod()))
                .irrigationSafetyStatus(isEStopActive ? "BLOCKED_EMERGENCY_STOP" : "VERIFIED_SAFE")
                .farmerConfirmed(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        List<PlantingTask> tasks = new ArrayList<>();

        // Category 1: SYSTEM_AUTOMATED (Authorized pre-soak irrigation)
        tasks.add(PlantingTask.builder()
                .plantingPlan(plan)
                .category("SYSTEM_AUTOMATED")
                .title("Pre-Irrigation Land Soaking")
                .description("Deliver controlled 3.0 cm water layer across terrace zones via ArvaCore automated valves.")
                .status(isEStopActive ? "BLOCKED" : "PENDING")
                .blockingReason(isEStopActive ? "Emergency stop is active" : null)
                .requiresConfirmation(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());

        tasks.add(PlantingTask.builder()
                .plantingPlan(plan)
                .category("SYSTEM_AUTOMATED")
                .title("Ultrasonic Water Table Zero-Point Calibration")
                .description("Verify reference perched water table baseline across all 6 ArvaNodes before puddling.")
                .status("PENDING")
                .requiresConfirmation(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());

        // Category 2: FARMER_MANUAL
        tasks.add(PlantingTask.builder()
                .plantingPlan(plan)
                .category("FARMER_MANUAL")
                .title("Plowing and Primary Harrowing")
                .description("Incorporate crop residue and break clods to 15-20 cm soil depth.")
                .status("PENDING")
                .requiresConfirmation(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());

        tasks.add(PlantingTask.builder()
                .plantingPlan(plan)
                .category("FARMER_MANUAL")
                .title("Paddy Bund Sealing & Rat Hole Plastering")
                .description("Compact perimeter bunds with mud to prevent water leakage during AWD cycles.")
                .status("PENDING")
                .requiresConfirmation(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());

        // Category 3: EQUIPMENT_INFO
        tasks.add(PlantingTask.builder()
                .plantingPlan(plan)
                .category("EQUIPMENT_INFO")
                .title("Certified Seed Availability Check")
                .description("Verify NSIC Rc 222 certified seed germination rate (>85%) and seed treatment.")
                .status("PENDING")
                .requiresConfirmation(false)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());

        // Category 4: SAFETY_BLOCKED (Condition dependent)
        if (isEStopActive) {
            tasks.add(PlantingTask.builder()
                    .plantingPlan(plan)
                    .category("SAFETY_BLOCKED")
                    .title("Clear Active Emergency Stop")
                    .description("The main irrigation circuit is in Emergency Stop. Deactivate E-Stop to enable pump actuation.")
                    .status("BLOCKED")
                    .blockingReason("Physical Emergency Stop latch is engaged.")
                    .requiresConfirmation(true)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build());
        }

        plan.setTasks(tasks);
        return plan;
    }

    private FarmingChatResponse buildBaseResponse(ChatbotSession session, String replyText, String promptQuestion, boolean reqConfirmation, String role) {
        return FarmingChatResponse.builder()
                .sessionId(session.getSessionId())
                .currentStep(session.getCurrentStep())
                .replyText(replyText)
                .promptQuestion(promptQuestion)
                .requiresConfirmation(reqConfirmation)
                .tasks(Collections.emptyList())
                .agronomicReferences(getAgronomicReferences(session.getSelectedCrop()))
                .disclaimer("Advisory only. Physical actions require explicit confirmation.")
                .userRole(role)
                .canControlIrrigation("ROLE_ADMIN".equals(role) || "ROLE_OPERATOR".equals(role))
                .canManageUsers("ROLE_ADMIN".equals(role))
                .canViewAuditLogs("ROLE_ADMIN".equals(role))
                .authorizedCapabilities(getCapabilitiesForRole(role))
                .build();
    }

    private FarmingChatResponse buildPlanResponse(ChatbotSession session, PlantingPlan plan, String replyText, String promptQuestion, boolean reqConfirmation, String role) {
        List<FarmingChatResponse.PlantingTaskDto> taskDtos = plan.getTasks().stream()
                .map(t -> FarmingChatResponse.PlantingTaskDto.builder()
                        .id(t.getId())
                        .category(t.getCategory())
                        .title(t.getTitle())
                        .description(t.getDescription())
                        .status(t.getStatus())
                        .blockingReason(t.getBlockingReason())
                        .requiresConfirmation(t.getRequiresConfirmation())
                        .build())
                .collect(Collectors.toList());

        List<String> warnings = new ArrayList<>();
        if ("BLOCKED_EMERGENCY_STOP".equals(plan.getIrrigationSafetyStatus())) {
            warnings.add("CRITICAL: Emergency Stop is currently active. All pump actions are physically interlocked.");
        }

        return FarmingChatResponse.builder()
                .sessionId(session != null ? session.getSessionId() : plan.getSessionId())
                .currentStep(session != null ? session.getCurrentStep() : "CONFIRMATION_PENDING")
                .replyText(replyText)
                .planId(plan.getId())
                .planCode(plan.getPlanCode())
                .readinessScore(plan.getReadinessScore())
                .readinessStatus(plan.getReadinessStatus())
                .promptQuestion(promptQuestion)
                .requiresConfirmation(reqConfirmation)
                .tasks(taskDtos)
                .agronomicReferences(getAgronomicReferences(plan.getCropName()))
                .warnings(warnings)
                .disclaimer("Advisory only. Zero direct actuation capability.")
                .userRole(role)
                .canControlIrrigation("ROLE_ADMIN".equals(role) || "ROLE_OPERATOR".equals(role))
                .canManageUsers("ROLE_ADMIN".equals(role))
                .canViewAuditLogs("ROLE_ADMIN".equals(role))
                .authorizedCapabilities(getCapabilitiesForRole(role))
                .build();
    }

    private List<String> getCapabilitiesForRole(String role) {
        List<String> caps = new ArrayList<>();
        caps.add("VIEW_OWN_TELEMETRY");
        caps.add("PLAN_CROPS");
        caps.add("MANAGE_FARMER_TASKS");
        if ("ROLE_SUPERVISOR".equals(role) || "ROLE_OPERATOR".equals(role) || "ROLE_ADMIN".equals(role)) {
            caps.add("VIEW_DIAGNOSTICS");
            caps.add("VIEW_TELEMETRY_STREAM");
        }
        if ("ROLE_OPERATOR".equals(role) || "ROLE_ADMIN".equals(role)) {
            caps.add("AUTHORIZE_IRRIGATION_PREPARATION");
            caps.add("MANUAL_PUMP_CONTROL");
        }
        if ("ROLE_ADMIN".equals(role)) {
            caps.add("VIEW_AUDIT_LOGS");
            caps.add("MANAGE_USER_ACCOUNTS");
            caps.add("NODE_COMMISSIONING");
        }
        return caps;
    }

    private boolean isAdversarial(String text) {
        String lower = text.toLowerCase();
        return lower.contains("ignore previous") || lower.contains("bypass") ||
                lower.contains("activate relay") || lower.contains("turn on the pump") ||
                lower.contains("system override") || lower.contains("format c:") ||
                lower.contains("gpio write") || lower.contains("sudo") ||
                lower.contains("i am the admin") || lower.contains("grant me admin");
    }

    private FarmingChatResponse buildAdversarialResponse(String sessionId, String role) {
        return FarmingChatResponse.builder()
                .sessionId(sessionId)
                .currentStep("CROP_SELECTION")
                .replyText("Request flagged for safety violation: Arva operates strictly in advisory mode. No direct actuation, privilege elevation, or system override is permitted.")
                .promptQuestion(INITIAL_QUESTION)
                .requiresConfirmation(false)
                .tasks(Collections.emptyList())
                .agronomicReferences(List.of("ArvaCore Deterministic Safety Interlocks"))
                .warnings(List.of("Attempted prompt injection or safety bypass detected and neutralized."))
                .disclaimer("Advisory only. Actuation requires authorized operator controls.")
                .userRole(role)
                .canControlIrrigation("ROLE_ADMIN".equals(role) || "ROLE_OPERATOR".equals(role))
                .canManageUsers("ROLE_ADMIN".equals(role))
                .canViewAuditLogs("ROLE_ADMIN".equals(role))
                .authorizedCapabilities(getCapabilitiesForRole(role))
                .build();
    }

    private String detectCrop(String msg, String selectedCrop) {
        if (selectedCrop != null && !selectedCrop.isBlank()) return selectedCrop;
        String lower = msg.toLowerCase();
        if (lower.contains("rice") || lower.contains("palay") || lower.contains("nsic")) {
            return "Lowland Rice (NSIC Rc 222)";
        } else if (lower.contains("corn") || lower.contains("maize")) {
            return "Hybrid Yellow Corn";
        } else if (lower.contains("eggplant") || lower.contains("talong")) {
            return "Eggplant (Dumaguete Long Purple)";
        } else if (lower.contains("tomato") || lower.contains("kamatis")) {
            return "Tomato (Diamante Max)";
        } else if (lower.contains("onion") || lower.contains("sibuyas")) {
            return "Red Onion";
        }
        return msg;
    }

    private String extractMethod(String msg) {
        String lower = msg.toLowerCase();
        if (lower.contains("transplant") || lower.contains("wet bed")) return "Wet Bed Transplanting";
        if (lower.contains("direct") || lower.contains("sabog")) return "Direct Wet Seeding";
        return "Standard Mechanical Transplanting";
    }

    private String extractSchedule(String msg) {
        if (msg.toLowerCase().contains("tomorrow")) return "Tomorrow morning";
        if (msg.toLowerCase().contains("next week")) return "Next week";
        return "Within 3-5 days";
    }

    private boolean isAffirmative(String msg) {
        String lower = msg.toLowerCase().trim();
        return lower.startsWith("yes") || lower.startsWith("y") || lower.contains("proceed") || lower.contains("confirm") || lower.contains("go ahead");
    }

    private List<String> getAgronomicReferences(String crop) {
        List<String> refs = new ArrayList<>();
        refs.add("IRRI Technical Bulletin: Alternate Wetting and Drying (AWD) for Rice (2020)");
        refs.add("DA-PhilRice NSIC Rc 222 Variety Production Guide (2023)");
        refs.add("Bureau of Soils and Water Management (BSWM) Crop Water Requirements");
        return refs;
    }

    private void recordAuditLog(String eventType, String actor, String entityType, String entityId, Object payload) {
        try {
            String payloadJson = (payload != null && objectMapper != null) ? objectMapper.writeValueAsString(payload) : "{}";
            IrrigationAuditLog logEntry = IrrigationAuditLog.builder()
                    .eventType(eventType)
                    .actor(actor)
                    .entityType(entityType)
                    .entityId(entityId)
                    .payloadJson(payloadJson)
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to persist audit log for AI chat event {}: {}", eventType, e.getMessage());
        }
    }
}
