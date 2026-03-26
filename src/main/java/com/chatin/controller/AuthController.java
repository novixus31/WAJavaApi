package com.chatin.controller;

import com.chatin.model.User;
import com.chatin.repository.UserRepository;
import com.chatin.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(UserRepository userRepository, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * POST /api/auth/login
     * Login with email and password, return JWT token
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        if (email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email and password are required"));
        }

        Optional<User> userOpt = userRepository.findByEmail(email.toLowerCase().trim());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid email or password"));
        }

        User user = userOpt.get();

        if (!user.comparePassword(password)) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid email or password"));
        }

        if (user.getIsActive() != null && !user.getIsActive()) {
            return ResponseEntity.status(403).body(Map.of("message", "Account is deactivated"));
        }

        // Update last login
        user.setLastLogin(Instant.now());
        userRepository.save(user);

        // Generate token
        String token = jwtTokenProvider.generateToken(
                user.getId(), user.getEmail(), user.getRole(), user.getCompanyId()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("user", buildUserResponse(user));

        logger.info("User logged in: {} ({})", user.getEmail(), user.getRole());
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/auth/verify
     * Verify JWT token and return user info
     */
    /**
     * POST /api/auth/seed
     * Create a test superadmin user (for initial setup only)
     */
    @PostMapping("/seed")
    public ResponseEntity<?> seed(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "admin@test.com");
        String password = body.getOrDefault("password", "123456");
        String role = body.getOrDefault("role", "superadmin");

        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "User already exists"));
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(password);
        user.hashPassword();
        user.setRole(role);
        user.setIsActive(true);
        userRepository.save(user);

        logger.info("Seed user created: {} ({})", email, role);
        return ResponseEntity.ok(Map.of("message", "User created", "user", buildUserResponse(user)));
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verify(HttpServletRequest request) {
        User user = (User) request.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid token"));
        }

        return ResponseEntity.ok(Map.of(
                "valid", true,
                "user", buildUserResponse(user)
        ));
    }

    private Map<String, Object> buildUserResponse(User user) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("email", user.getEmail());
        userMap.put("role", user.getRole());
        userMap.put("companyId", user.getCompanyId());
        userMap.put("companyName", user.getCompanyName());
        userMap.put("isActive", user.getIsActive());
        userMap.put("lastLogin", user.getLastLogin());
        return userMap;
    }
}
