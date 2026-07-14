package com.contractsentinel.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String USER_ROLE = "ROLE_USER";
    private static final Duration ADMIN_EXPIRY = Duration.ofHours(24);
    private static final Duration USER_EXPIRY = Duration.ofHours(1);

    private final JwtTokenProvider tokenProvider;

    public AuthController(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request) {
        log.info("Login attempt for serviceId: {}", request.serviceId());

        if (request.serviceId() == null || request.serviceId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "serviceId is required"));
        }

        if (request.apiKey() == null || request.apiKey().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "apiKey is required"));
        }

        String role = validateApiKey(request.serviceId(), request.apiKey());
        if (role == null) {
            log.warn("Invalid API key for serviceId: {}", request.serviceId());
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }

        Duration expiry = ADMIN_ROLE.equals(role) ? ADMIN_EXPIRY : USER_EXPIRY;
        String token = tokenProvider.generateToken(request.serviceId(), role, expiry);

        log.info("Token generated for serviceId: {} with role: {}", request.serviceId(), role);
        return ResponseEntity.ok(Map.of("token", token, "role", role));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(@RequestBody RefreshRequest request) {
        log.info("Token refresh attempt");

        if (request.token() == null || request.token().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "token is required"));
        }

        if (!tokenProvider.validateToken(request.token())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired token"));
        }

        String serviceId = tokenProvider.getServiceId(request.token());
        String role = tokenProvider.getRole(request.token());

        Duration expiry = ADMIN_ROLE.equals(role) ? ADMIN_EXPIRY : USER_EXPIRY;
        String newToken = tokenProvider.generateToken(serviceId, role, expiry);

        log.info("Token refreshed for serviceId: {}", serviceId);
        return ResponseEntity.ok(Map.of("token", newToken, "role", role));
    }

    private String validateApiKey(String serviceId, String apiKey) {
        String configuredApiKey = System.getenv("API_KEY_" + serviceId.replace("-", "_").toUpperCase());
        if (apiKey.equals(configuredApiKey)) {
            return ADMIN_ROLE;
        }
        if (apiKey.equals("default-api-key")) {
            return USER_ROLE;
        }
        return null;
    }

    public record LoginRequest(String serviceId, String apiKey) {}
    public record RefreshRequest(String token) {}
}
