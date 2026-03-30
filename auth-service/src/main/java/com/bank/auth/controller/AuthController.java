package com.bank.auth.controller;

import com.bank.shared.model.TokenResponse;
import com.bank.shared.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody AuthService.AuthRequest request) {
        TokenResponse response = authService.authenticate(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthService.RefreshTokenResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        AuthService.RefreshTokenResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/token")
    public ResponseEntity<Void> revokeToken(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        authService.revokeToken(token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/validate")
    public ResponseEntity<AuthService.TokenValidationResult> validate(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        AuthService.TokenValidationResult result = authService.validateToken(token);
        return ResponseEntity.ok(result);
    }
}
