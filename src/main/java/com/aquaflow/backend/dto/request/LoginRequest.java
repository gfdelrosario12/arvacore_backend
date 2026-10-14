package com.aquaflow.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    private String username;
    private String identifier;

    @NotBlank
    private String password;

    public String getUsername() {
        return (username != null && !username.trim().isEmpty()) ? username : identifier;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIdentifier() {
        return identifier != null ? identifier : username;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
        if (this.username == null) {
            this.username = identifier;
        }
    }
}