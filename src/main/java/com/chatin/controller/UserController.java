package com.chatin.controller;

import com.chatin.model.Company;
import com.chatin.model.User;
import com.chatin.model.UserAccount;
import com.chatin.repository.CompanyRepository;
import com.chatin.repository.UserAccountRepository;
import com.chatin.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserRepository userRepository;
    private final UserAccountRepository userAccountRepository;
    private final CompanyRepository companyRepository;

    public UserController(UserRepository userRepository, UserAccountRepository userAccountRepository, CompanyRepository companyRepository) {
        this.userRepository = userRepository;
        this.userAccountRepository = userAccountRepository;
        this.companyRepository = companyRepository;
    }

    private Map<String, Object> mapUserToResponse(User u) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", u.getId());
        map.put("userEmail", u.getEmail());
        map.put("role", u.getRole());
        
        String companyId = u.getCompanyId();
        String cName = u.getCompanyName();

        // If companyName is somehow missing, try to resolve it dynamically
        if ((cName == null || cName.isEmpty()) && companyId != null) {
            Optional<Company> compOpt = companyRepository.findById(companyId);
            if (compOpt.isPresent()) {
                cName = compOpt.get().getName();
                // Optionally save this back to user, but memory is fine for response
                u.setCompanyName(cName); 
            }
        }

        Map<String, String> companyData = new HashMap<>();
        companyData.put("_id", companyId);
        companyData.put("name", cName != null ? cName : "");
        
        map.put("companyId", companyData);
        map.put("companyName", cName != null ? cName : "");
        map.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "");
        
        return map;
    }

    /**
     * GET /api/users - List users based on role
     */
    @GetMapping
    public ResponseEntity<?> getUsers(HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        String companyIdParam = (String) request.getAttribute("userCompanyId");

        List<User> users;
        if ("superadmin".equals(role)) {
            users = userRepository.findAll();
        } else {
            users = userRepository.findByCompanyId(companyIdParam);
        }

        List<Map<String, Object>> mappedUsers = new ArrayList<>();
        for (User u : users) {
             mappedUsers.add(mapUserToResponse(u));
        }

        return ResponseEntity.ok(Map.of("success", true, "users", mappedUsers));
    }

    /**
     * GET /api/users/:id - Get single user
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable String id) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "User not found"));
        }
        return ResponseEntity.ok(mapUserToResponse(userOpt.get()));
    }

    /**
     * POST /api/users - Create new user
     */
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        String userCompanyId = (String) request.getAttribute("userCompanyId");

        if (!"superadmin".equals(role) && !"admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        String email = body.get("userEmail");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
        }

        if (userRepository.existsByEmail(email.toLowerCase().trim())) {
            return ResponseEntity.status(409).body(Map.of("message", "Email already exists"));
        }

        User user = new User();
        user.setEmail(email.toLowerCase().trim());
        user.setPassword(body.getOrDefault("password", "password123"));
        user.hashPassword();
        user.setRole(body.getOrDefault("role", "user"));
        user.setIsActive(true);

        String companyId = body.get("companyId");
        if ("superadmin".equals(role) && companyId != null) {
            user.setCompanyId(companyId);
        } else {
            user.setCompanyId(userCompanyId);
        }

        // Resolving company name properly if not provided
        String compName = body.get("companyName");
        if ((compName == null || compName.isEmpty()) && user.getCompanyId() != null) {
            Optional<Company> compOpt = companyRepository.findById(user.getCompanyId());
            if (compOpt.isPresent()) {
                compName = compOpt.get().getName();
            }
        }
        user.setCompanyName(compName);

        User saved = userRepository.save(user);

        logger.info("User created: {} ({})", saved.getEmail(), saved.getRole());
        return ResponseEntity.status(201).body(mapUserToResponse(saved));
    }

    /**
     * PATCH /api/users/:id - Update user
     */
    @PatchMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable String id, @RequestBody Map<String, String> body,
                                        HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        if (!"superadmin".equals(role) && !"admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "User not found"));
        }

        User user = userOpt.get();

        if (body.containsKey("email")) user.setEmail(body.get("email").toLowerCase().trim());
        if (body.containsKey("role")) user.setRole(body.get("role"));
        if (body.containsKey("companyId")) user.setCompanyId(body.get("companyId"));
        if (body.containsKey("companyName")) {
             user.setCompanyName(body.get("companyName"));
        } else if (body.containsKey("companyId")) {
             // Auto resolve new company name if companyId is changed but companyName is not provided
             Optional<Company> compOpt = companyRepository.findById(body.get("companyId"));
             compOpt.ifPresent(c -> user.setCompanyName(c.getName()));
        }
        
        if (body.containsKey("password") && !body.get("password").isBlank()) {
            user.setPassword(body.get("password"));
            user.hashPassword();
        }

        User updated = userRepository.save(user);

        return ResponseEntity.ok(mapUserToResponse(updated));
    }

    /**
     * PATCH /api/users/:id/role - Update user role
     */
    @PatchMapping("/{id}/role")
    public ResponseEntity<?> updateRole(@PathVariable String id, @RequestBody Map<String, String> body,
                                        HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        if (!"superadmin".equals(role) && !"admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "User not found"));
        }

        User user = userOpt.get();
        user.setRole(body.get("role"));
        userRepository.save(user);

        return ResponseEntity.ok(mapUserToResponse(user));
    }

    /**
     * DELETE /api/users/:id - Delete user
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable String id, HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        if (!"superadmin".equals(role) && !"admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "User not found"));
        }

        // Remove user-account associations
        userAccountRepository.deleteByUserId(id);
        userRepository.deleteById(id);

        logger.info("User deleted: {}", id);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }
}
