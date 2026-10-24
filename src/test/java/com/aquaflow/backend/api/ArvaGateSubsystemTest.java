package com.aquaflow.backend.api;

import com.aquaflow.backend.domain.ArvaGateService;
import com.aquaflow.backend.dto.request.ArvaGateDispatchCommandRequest;
import com.aquaflow.backend.entity.ArvaGate;
import com.aquaflow.backend.entity.ArvaGateCommand;
import com.aquaflow.backend.entity.ArvaGateDevice;
import com.aquaflow.backend.persistence.ArvaGateCommandRepository;
import com.aquaflow.backend.persistence.ArvaGateDeviceRepository;
import com.aquaflow.backend.persistence.ArvaGateEventRepository;
import com.aquaflow.backend.persistence.ArvaGateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ArvaGateSubsystemTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ArvaGateService gateService;

    @Autowired
    private ArvaGateDeviceRepository deviceRepository;

    @Autowired
    private ArvaGateRepository gateRepository;

    @Autowired
    private ArvaGateCommandRepository commandRepository;

    @Autowired
    private ArvaGateEventRepository eventRepository;

    private final String DEVICE_ID = "ARVAGATE-CTRL-01";
    private final String GATEWAY_KEY = "ed630e681ef735f36954469b48f832f210286246ca95fff62e62086cab74e8a9";

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        eventRepository.deleteAll();
        commandRepository.deleteAll();
        gateRepository.deleteAll();
        deviceRepository.deleteAll();

        deviceRepository.save(ArvaGateDevice.builder()
                .deviceId(DEVICE_ID)
                .displayName("ArvaGate Canal Controller")
                .fieldId(1L)
                .firmwareVersion("1.0.0")
                .connectivityStatus("ONLINE")
                .build());

        gateRepository.saveAll(List.of(
                ArvaGate.builder().deviceId(DEVICE_ID).gateIndex(1).gateLabel("Gate 1 (Inflow)").currentState("CLOSED").requestedState("CLOSED").physicalConfirmed(true).build(),
                ArvaGate.builder().deviceId(DEVICE_ID).gateIndex(2).gateLabel("Gate 2 (Lateral)").currentState("CLOSED").requestedState("CLOSED").physicalConfirmed(true).build(),
                ArvaGate.builder().deviceId(DEVICE_ID).gateIndex(3).gateLabel("Gate 3 (Distal)").currentState("CLOSED").requestedState("CLOSED").physicalConfirmed(true).build()
        ));
    }

    @Test
    @DisplayName("Operator can query 3-gate status and see all gates independently")
    @WithMockUser(username = "operator1", roles = {"OPERATOR"})
    void testGetGateStatus() throws Exception {
        mockMvc.perform(get("/api/v1/gates/status/" + DEVICE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device.deviceId").value(DEVICE_ID))
                .andExpect(jsonPath("$.gates.length()").value(3))
                .andExpect(jsonPath("$.gates[0].gateIndex").value(1))
                .andExpect(jsonPath("$.gates[0].currentState").value("CLOSED"))
                .andExpect(jsonPath("$.gates[1].gateIndex").value(2))
                .andExpect(jsonPath("$.gates[2].gateIndex").value(3));
    }

    @Test
    @DisplayName("Operator can dispatch independent OPEN command for Gate 2 only")
    @WithMockUser(username = "operator1", roles = {"OPERATOR"})
    void testDispatchCommandForGate2() throws Exception {
        String json = """
            {
              "targetGates": "2",
              "action": "OPEN",
              "durationSeconds": 15,
              "rationale": "Irrigating mid-terrace lateral"
            }
        """;

        mockMvc.perform(post("/api/v1/gates/command/" + DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.action").value("OPEN"))
                .andExpect(jsonPath("$.targetGates").value("2"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // Verify gate 2 requested state updated to OPEN while gates 1 and 3 remain CLOSED
        ArvaGate g1 = gateRepository.findByDeviceIdAndGateIndex(DEVICE_ID, 1).orElseThrow();
        ArvaGate g2 = gateRepository.findByDeviceIdAndGateIndex(DEVICE_ID, 2).orElseThrow();
        ArvaGate g3 = gateRepository.findByDeviceIdAndGateIndex(DEVICE_ID, 3).orElseThrow();

        assertThat(g1.getRequestedState()).isEqualTo("CLOSED");
        assertThat(g2.getRequestedState()).isEqualTo("OPEN");
        assertThat(g3.getRequestedState()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("Hardware controller with GATEWAY_KEY can poll pending command and acknowledge execution")
    void testHardwarePollingAndAck() throws Exception {
        // 1. Dispatch command
        gateService.dispatchCommand(DEVICE_ID, ArvaGateDispatchCommandRequest.builder()
                .targetGates("1,3")
                .action("OPEN")
                .rationale("Open intake and tail gates")
                .build(), "operator1");

        // 2. Hardware polls for pending command
        String mvcResult = mockMvc.perform(get("/api/v1/gates/pending-command/" + DEVICE_ID)
                        .header("X-ArvaCore-Gateway-Key", GATEWAY_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("OPEN"))
                .andExpect(jsonPath("$.targetGates").value("1,3"))
                .andReturn().getResponse().getContentAsString();

        assertThat(mvcResult).contains("CMD-GATE-");

        // Extract commandId from DB
        ArvaGateCommand cmd = commandRepository.findByDeviceIdAndStatusOrderByCreatedAtAsc(DEVICE_ID, "PENDING").get(0);

        // 3. Hardware acknowledges command execution with gate states
        String ackJson = """
            {
              "deviceId": "%s",
              "status": "EXECUTED",
              "details": "Actuators reached open limit switches",
              "gate1State": "OPEN",
              "gate2State": "CLOSED",
              "gate3State": "OPEN"
            }
        """.formatted(DEVICE_ID);

        mockMvc.perform(post("/api/v1/gates/ack/" + cmd.getCommandId())
                        .header("X-ArvaCore-Gateway-Key", GATEWAY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ackJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));

        // Verify database state
        ArvaGateCommand updatedCmd = commandRepository.findByCommandId(cmd.getCommandId()).orElseThrow();
        assertThat(updatedCmd.getStatus()).isEqualTo("EXECUTED");

        ArvaGate g1 = gateRepository.findByDeviceIdAndGateIndex(DEVICE_ID, 1).orElseThrow();
        ArvaGate g2 = gateRepository.findByDeviceIdAndGateIndex(DEVICE_ID, 2).orElseThrow();
        ArvaGate g3 = gateRepository.findByDeviceIdAndGateIndex(DEVICE_ID, 3).orElseThrow();

        assertThat(g1.getCurrentState()).isEqualTo("OPEN");
        assertThat(g2.getCurrentState()).isEqualTo("CLOSED");
        assertThat(g3.getCurrentState()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("Unauthorized users without OPERATOR or ADMIN role are rejected from dispatching gate commands")
    @WithMockUser(username = "farmer1", roles = {"FARMER"})
    void testUnauthorizedFarmerCannotDispatch() throws Exception {
        String json = """
            {
              "targetGates": "1",
              "action": "OPEN"
            }
        """;

        mockMvc.perform(post("/api/v1/gates/command/" + DEVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
    }
}

