package com.veterinaria.auth.dto;

public record LoginResponse(
        String token,
        String type,
        long expiresIn
) {

    public static LoginResponse conToken(String token, long expiracionMs) {
        return new LoginResponse(token, "Bearer", expiracionMs / 1000);
    }
}
