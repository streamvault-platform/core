package io.streamvault.core.application.auth;

public record TokenResponse(String accessToken, String refreshToken) {}
