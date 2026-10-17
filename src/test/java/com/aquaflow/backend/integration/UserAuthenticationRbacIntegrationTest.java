package com.aquaflow.backend.integration;

import com.aquaflow.backend.dto.request.LoginRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class UserAuthenticationRbacIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @ParameterizedTest(name = "Authenticate identifier [{0}] -> expected user [{3}], role [{2}], email [{4}]")
    @CsvSource({
            "admin, password123, ROLE_ADMIN, admin, admin@arvacore.io",
            "admin@arvacore.io, password123, ROLE_ADMIN, admin, admin@arvacore.io",
            "Gladwin, password123, ROLE_ADMIN, Gladwin, gladwin@aquaflow.io",
            "gladwin@aquaflow.io, password123, ROLE_ADMIN, Gladwin, gladwin@aquaflow.io",
            "Pranz, password123, ROLE_OPERATOR, Pranz, pranz@aquaflow.io",
            "pranz@aquaflow.io, password123, ROLE_OPERATOR, Pranz, pranz@aquaflow.io",
            "Jenice, password123, ROLE_SUPERVISOR, Jenice, jenice@aquaflow.io",
            "jenice@aquaflow.io, password123, ROLE_SUPERVISOR, Jenice, jenice@aquaflow.io",
            "MrFarmer, password123, ROLE_USER, MrFarmer, mr.farmer@aquaflow.io",
            "mr.farmer@aquaflow.io, password123, ROLE_USER, MrFarmer, mr.farmer@aquaflow.io",
            "farmer_m, password123, ROLE_USER, MrFarmer, mr.farmer@aquaflow.io",
            "MsFarmer, password123, ROLE_USER, MsFarmer, ms.farmer@aquaflow.io",
            "ms.farmer@aquaflow.io, password123, ROLE_USER, MsFarmer, ms.farmer@aquaflow.io",
            "farmer_f, password123, ROLE_USER, MsFarmer, ms.farmer@aquaflow.io"
    })
    @DisplayName("Verify authentication and JWT issuance for authorized test accounts and aliases")
    public void shouldAuthenticateAuthorizedAccounts(String identifier, String password, String expectedRole, String expectedUsername, String expectedEmail) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(identifier);
        request.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(jsonNode.hasNonNull("accessToken")).isTrue();
        assertThat(jsonNode.hasNonNull("refreshToken")).isTrue();
        assertThat(jsonNode.get("expiresIn").asLong()).isGreaterThan(0L);

        JsonNode userNode = jsonNode.get("user");
        assertThat(userNode).isNotNull();
        assertThat(userNode.get("username").asText()).isEqualTo(expectedUsername);
        assertThat(userNode.get("role").asText()).isEqualTo(expectedRole);
        assertThat(userNode.get("email").asText()).isEqualTo(expectedEmail);

        // Also verify /api/v1/auth/me returns the authenticated user
        String token = jsonNode.get("accessToken").asText();
        MvcResult meResult = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode meNode = objectMapper.readTree(meResult.getResponse().getContentAsString());
        assertThat(meNode.get("username").asText()).isEqualTo(expectedUsername);
        assertThat(meNode.get("role").asText()).isEqualTo(expectedRole);
        assertThat(meNode.get("email").asText()).isEqualTo(expectedEmail);
    }

    @Test
    @DisplayName("Verify account isolation: admin and Gladwin have distinct user IDs")
    public void shouldMaintainIndependentAdminAccounts() throws Exception {
        JsonNode adminJson = loginAndGetBody("admin", "password123");
        JsonNode gladwinJson = loginAndGetBody("Gladwin", "password123");

        long adminId = adminJson.get("user").get("id").asLong();
        long gladwinId = gladwinJson.get("user").get("id").asLong();

        assertThat(adminId).isNotEqualTo(gladwinId);
        assertThat(adminJson.get("user").get("username").asText()).isEqualTo("admin");
        assertThat(gladwinJson.get("user").get("username").asText()).isEqualTo("Gladwin");
        assertThat(adminJson.get("user").get("email").asText()).isEqualTo("admin@arvacore.io");
        assertThat(gladwinJson.get("user").get("email").asText()).isEqualTo("gladwin@aquaflow.io");
    }

    @Test
    @DisplayName("Verify account isolation: MrFarmer and MsFarmer have distinct user IDs and alias resolution")
    public void shouldMaintainIndependentFarmerAccounts() throws Exception {
        JsonNode mrFarmer = loginAndGetBody("farmer_m", "password123");
        JsonNode msFarmer = loginAndGetBody("farmer_f", "password123");

        long mrId = mrFarmer.get("user").get("id").asLong();
        long msId = msFarmer.get("user").get("id").asLong();

        assertThat(mrId).isNotEqualTo(msId);
        assertThat(mrFarmer.get("user").get("username").asText()).isEqualTo("MrFarmer");
        assertThat(msFarmer.get("user").get("username").asText()).isEqualTo("MsFarmer");
        assertThat(mrFarmer.get("user").get("email").asText()).isEqualTo("mr.farmer@aquaflow.io");
        assertThat(msFarmer.get("user").get("email").asText()).isEqualTo("ms.farmer@aquaflow.io");
    }

    @Test
    @DisplayName("Verify authentication rejection on invalid password and non-existent username")
    public void shouldRejectInvalidCredentials() throws Exception {
        LoginRequest badPassword = new LoginRequest();
        badPassword.setUsername("admin");
        badPassword.setPassword("wrong_password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPassword)))
                .andExpect(status().isUnauthorized());

        LoginRequest badUser = new LoginRequest();
        badUser.setUsername("nonexistent_user");
        badUser.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badUser)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Verify unauthorized access (401) without JWT or with malformed token")
    public void shouldRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/zones"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/zones")
                        .header("Authorization", "Bearer invalid.malformed.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Verify Role-Based Access Control (RBAC) across ADMIN, OPERATOR, SUPERVISOR, and USER")
    public void shouldEnforceRoleBasedAccessControl() throws Exception {
        String adminToken = loginAndGetToken("admin", "password123");
        String operatorToken = loginAndGetToken("Pranz", "password123");
        String supervisorToken = loginAndGetToken("Jenice", "password123");
        String userToken = loginAndGetToken("MrFarmer", "password123");

        // 1. /api/v1/audit/system requires ROLE_ADMIN
        mockMvc.perform(get("/api/v1/audit/system")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit/system")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/audit/system")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/audit/system")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // 2. /api/v1/system/master-state allows ROLE_ADMIN, ROLE_OPERATOR, ROLE_SUPERVISOR; forbids ROLE_USER
        mockMvc.perform(get("/api/v1/system/master-state")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/system/master-state")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/system/master-state")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/system/master-state")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // 3. /api/v1/telemetry/stream/summary allows ROLE_ADMIN, ROLE_OPERATOR, ROLE_SUPERVISOR; forbids ROLE_USER
        mockMvc.perform(get("/api/v1/telemetry/stream/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/telemetry/stream/summary")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/telemetry/stream/summary")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/telemetry/stream/summary")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // 4. /api/v1/zones allows any authenticated user (ADMIN, OPERATOR, SUPERVISOR, USER)
        mockMvc.perform(get("/api/v1/zones")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/zones")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/zones")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/zones")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }

    private JsonNode loginAndGetBody(String username, String password) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        return loginAndGetBody(username, password).get("accessToken").asText();
    }
}
