package com.example.server.dto;

import lombok.Data;

@Data
public class JwtResponse {
    private String username;
    private String accessToken;
    private String tokenType = "Bearer";

    public JwtResponse(String username, String accessToken) {
        this.username = username;
        this.accessToken = accessToken;
    }
}
