package com.aquaflow.backend.controller;

import com.aquaflow.backend.api.IrrigationController;
import com.aquaflow.backend.domain.EmergencyStopService;
import com.aquaflow.backend.domain.IrrigationDecisionService;
import com.aquaflow.backend.domain.ManualControlService;
import com.aquaflow.backend.dto.request.EmergencyStopRequest;
import com.aquaflow.backend.dto.request.ManualStartRequest;
import com.aquaflow.backend.dto.request.ManualStopRequest;
import com.aquaflow.backend.dto.response.CommandStatusResponse;
import com.aquaflow.backend.dto.response.FieldIrrigationStatusResponse;
import com.aquaflow.backend.entity.CommandLifecycleState;
import com.aquaflow.backend.infrastructure.exception.GlobalExceptionHandler;
import com.aquaflow.backend.infrastructure.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IrrigationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ManualControlService manualControlService;

    @Mock
    private EmergencyStopService emergencyStopService;

    @Mock
    private IrrigationDecisionService decisionService;

    @InjectMocks
    private IrrigationController irrigationController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(irrigationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldStartManualIrrigationSuccessfully() throws Exception {
        ManualStartRequest request = ManualStartRequest.builder()
                .operatorId("OP-100")
                .fieldId(1L)
                .rationale("Manual override for testing")
                .durationMinutes(30)
                .build();

        CommandStatusResponse response = CommandStatusResponse.builder()
                .correlationId("test-correlation-1")
                .fieldId(1L)
                .operatorId("OP-100")
                .status(CommandLifecycleState.QUEUED)
                .commandType("MANUAL_START")
                .createdAt(LocalDateTime.now())
                .build();

        when(manualControlService.startManualIrrigation(any(ManualStartRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/irrigation/manual/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.correlationId").value("test-correlation-1"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.commandType").value("MANUAL_START"));
    }

    @Test
    void shouldStopManualIrrigationSuccessfully() throws Exception {
        ManualStopRequest request = ManualStopRequest.builder()
                .operatorId("OP-100")
                .fieldId(1L)
                .rationale("Stop manual override")
                .build();

        CommandStatusResponse response = CommandStatusResponse.builder()
                .correlationId("test-correlation-2")
                .fieldId(1L)
                .operatorId("OP-100")
                .status(CommandLifecycleState.QUEUED)
                .commandType("MANUAL_STOP")
                .createdAt(LocalDateTime.now())
                .build();

        when(manualControlService.stopManualIrrigation(any(ManualStopRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/irrigation/manual/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.correlationId").value("test-correlation-2"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.commandType").value("MANUAL_STOP"));
    }

    @Test
    void shouldExecuteEmergencyStopSuccessfully() throws Exception {
        EmergencyStopRequest request = EmergencyStopRequest.builder()
                .operatorId("ADMIN-007")
                .fieldId(1L)
                .reason("Critical valve leak")
                .build();

        CommandStatusResponse response = CommandStatusResponse.builder()
                .correlationId("test-emergency-correlation")
                .fieldId(1L)
                .operatorId("ADMIN-007")
                .status(CommandLifecycleState.QUEUED)
                .commandType("EMERGENCY_STOP")
                .createdAt(LocalDateTime.now())
                .build();

        when(emergencyStopService.executeEmergencyStop(any(EmergencyStopRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/irrigation/emergency-stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.correlationId").value("test-emergency-correlation"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.commandType").value("EMERGENCY_STOP"));
    }

    @Test
    void shouldGetCommandStatusSuccessfully() throws Exception {
        CommandStatusResponse response = CommandStatusResponse.builder()
                .correlationId("test-correlation-1")
                .fieldId(1L)
                .operatorId("OP-100")
                .status(CommandLifecycleState.ACKNOWLEDGED)
                .commandType("MANUAL_START")
                .createdAt(LocalDateTime.now())
                .build();

        when(manualControlService.getCommandStatus("test-correlation-1")).thenReturn(response);

        mockMvc.perform(get("/api/v1/irrigation/command/test-correlation-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correlationId").value("test-correlation-1"))
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }

    @Test
    void shouldReturn404OnUnknownCommandStatus() throws Exception {
        when(manualControlService.getCommandStatus("unknown-id"))
                .thenThrow(new ResourceNotFoundException("Command not found"));

        mockMvc.perform(get("/api/v1/irrigation/command/unknown-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnSystemStatusSuccessfully() throws Exception {
        FieldIrrigationStatusResponse statusResponse = FieldIrrigationStatusResponse.builder()
                .fieldId(1L)
                .fieldName("Main Rice Field")
                .isIrrigating(false)
                .activeIrrigatingNodesCount(0)
                .todayDeliveredVolumeLiters(150.0)
                .lastUpdated(LocalDateTime.now())
                .build();

        when(decisionService.getFieldIrrigationStatus(eq(1L))).thenReturn(statusResponse);

        mockMvc.perform(get("/api/v1/irrigation/status").param("fieldId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fieldId").value(1))
                .andExpect(jsonPath("$.fieldName").value("Main Rice Field"))
                .andExpect(jsonPath("$.isIrrigating").value(false))
                .andExpect(jsonPath("$.todayDeliveredVolumeLiters").value(150.0));
    }
}

