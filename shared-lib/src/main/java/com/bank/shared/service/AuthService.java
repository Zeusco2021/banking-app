package com.bank.shared.service;

import com.bank.shared.model.TokenResponse;

public interface AuthService {

    record AuthRequest(String username, String password) {}

    record TokenValidationResult(boolean valid, String userId, String[] roles) {}

    record RefreshTokenResponse(String accessToken, long expiresIn) {}

    TokenResponse authenticate(AuthRequest request);

    TokenValidationResult validateToken(String jwtToken);

    void revokeToken(String jwtToken);

    RefreshTokenResponse refreshToken(String refreshToken);
}
