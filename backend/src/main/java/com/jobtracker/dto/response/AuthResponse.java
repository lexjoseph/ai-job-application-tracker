package com.jobtracker.dto.response;

public record AuthResponse(String accessToken, String tokenType, long expiresInMs) {

    public static AuthResponse of(String token, long expiresInMs) {
        return new AuthResponse(token, "Bearer", expiresInMs);
    }
}
