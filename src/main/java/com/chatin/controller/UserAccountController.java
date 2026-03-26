package com.chatin.controller;

import com.chatin.model.UserAccount;
import com.chatin.repository.UserAccountRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/user-accounts")
public class UserAccountController {

    private final UserAccountRepository userAccountRepository;

    public UserAccountController(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * GET /api/user-accounts - Get user-account associations for the current user
     */
    @GetMapping
    public ResponseEntity<?> getUserAccounts(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        List<UserAccount> userAccounts = userAccountRepository.findByUserIdOrderByOrderAsc(userId);
        return ResponseEntity.ok(userAccounts);
    }

    /**
     * GET /api/user-accounts/user/:userId - Get accounts for a specific user
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getAccountsForUser(@PathVariable String userId) {
        List<UserAccount> userAccounts = userAccountRepository.findByUserIdOrderByOrderAsc(userId);
        return ResponseEntity.ok(userAccounts);
    }

    /**
     * GET /api/user-accounts/account/:accountId - Get users for a specific account
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<?> getUsersForAccount(@PathVariable String accountId) {
        List<UserAccount> userAccounts = userAccountRepository.findByAccountId(accountId);
        return ResponseEntity.ok(userAccounts);
    }

    /**
     * POST /api/user-accounts - Create user-account association
     */
    @PostMapping
    public ResponseEntity<?> createUserAccount(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        if (!"superadmin".equals(role) && !"admin".equals(role) && !"manager".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        String userId = (String) body.get("userId");
        String accountId = (String) body.get("accountId");

        Optional<UserAccount> existing = userAccountRepository.findByUserIdAndAccountId(userId, accountId);
        if (existing.isPresent()) {
            return ResponseEntity.status(409).body(Map.of("message", "Association already exists"));
        }

        UserAccount ua = new UserAccount();
        ua.setUserId(userId);
        ua.setAccountId(accountId);
        ua.setOrder(body.get("order") != null ? Integer.parseInt(body.get("order").toString()) : 0);

        UserAccount saved = userAccountRepository.save(ua);
        return ResponseEntity.status(201).body(saved);
    }

    /**
     * PUT /api/user-accounts/order - Update order of accounts for a user
     */
    @PutMapping("/order")
    public ResponseEntity<?> updateOrder(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orderedAccounts = (List<Map<String, Object>>) body.get("accounts");

        if (orderedAccounts != null) {
            for (Map<String, Object> item : orderedAccounts) {
                String accountId = (String) item.get("accountId");
                Integer order = Integer.parseInt(item.get("order").toString());

                Optional<UserAccount> uaOpt = userAccountRepository.findByUserIdAndAccountId(userId, accountId);
                if (uaOpt.isPresent()) {
                    UserAccount ua = uaOpt.get();
                    ua.setOrder(order);
                    userAccountRepository.save(ua);
                }
            }
        }

        return ResponseEntity.ok(Map.of("message", "Order updated"));
    }

    /**
     * DELETE /api/user-accounts/:id - Remove user-account association
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUserAccount(@PathVariable String id, HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        if (!"superadmin".equals(role) && !"admin".equals(role) && !"manager".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        userAccountRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Association removed"));
    }
}
