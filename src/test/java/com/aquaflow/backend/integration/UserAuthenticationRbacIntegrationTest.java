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

    @ParameterizedTest(name = "Authenticate account {0} expecting role {2}")
    @CsvSource({
            "admin, admin_pass, ROLE_ADMIN, admin@arvacore.io",
            "Gladwin, password123, ROLE_ADMIN, gladwin@aquaflow.io",
            "Pranz, password123, ROLE_OPERATOR, pranz@aquaflow.io",
            "Jenice, password123, ROLE_SUPERVISOR, jenice@aquaflow.io",
            "MrFarmer, password123, ROLE_USER, mr.farmer@aquaflow.io",
            "farmer_m, password123, ROLE_USER, farmer_m@aquaflow.io",
            "MsFarmer, password123, ROLE_USER, ms.farmer@aquaflow.io",
            "farmer_f, password123, ROLE_USER, farmer_f@aquaflow.io"
    })
    @DisplayName("Verify authentication and JWT issuance for authorized test accounts")
    public void shouldAuthenticateAuthorizedAccounts(String username, String password, String expectedRole, String expectedEmail) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
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
        assertThat(userNode.get("username").asText()).isEqualTo(username);
        assertThat(userNode.get("role").asText()).isEqualTo(expectedRole);
        assertThat(userNode.get("email").asText()).isEqualTo(expectedEmail);
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
        // Protected endpoint without token -> 401
        mockMvc.perform(get("/api/v1/zones"))
                .andExpect(status().isUnauthorized());

        // Protected endpoint with invalid/malformed token -> 401
        mockMvc.perform(get("/api/v1/zones")
                        .header("Authorization", "Bearer invalid.malformed.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Verify Role-Based Access Control (RBAC) across ADMIN, OPERATOR, SUPERVISOR, and USER")
    public void shouldEnforceRoleBasedAccessControl() throws Exception {
        // 1. Obtain tokens for different roles
        String adminToken = loginAndGetToken("admin", "admin_pass");
        String operatorToken = loginAndGetToken("Pranz", "password123");
        String supervisorToken = loginAndGetToken("Jenice", "password123");
        String userToken = loginAndGetToken("MrFarmer", "password123");

        // 2. /api/v1/audit/system requires ROLE_ADMIN
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

        // 3. /api/v1/system/master-state requires ROLE_ADMIN or ROLE_OPERATOR
        mockMvc.perform(get("/api/v1/system/master-state")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/system/master-state")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/system/master-state")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/system/master-state")
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

    private String loginAndGetToken(String username, String password) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.get("accessToken").asText();
    }
}

