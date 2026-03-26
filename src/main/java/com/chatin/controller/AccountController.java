package com.chatin.controller;

import com.chatin.model.Account;
import com.chatin.model.User;
import com.chatin.model.UserAccount;
import com.chatin.repository.*;
import com.chatin.service.WhatsAppManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private static final Logger logger = LoggerFactory.getLogger(AccountController.class);

    private final AccountRepository accountRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserRepository userRepository;
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final ReactionRepository reactionRepository;
    private final WhatsAppAuthRepository whatsAppAuthRepository;
    private final WhatsAppManager whatsAppManager;

    public AccountController(AccountRepository accountRepository,
                             UserAccountRepository userAccountRepository,
                             UserRepository userRepository,
                             ChatRepository chatRepository,
                             MessageRepository messageRepository,
                             ReactionRepository reactionRepository,
                             WhatsAppAuthRepository whatsAppAuthRepository,
                             WhatsAppManager whatsAppManager) {
        this.accountRepository = accountRepository;
        this.userAccountRepository = userAccountRepository;
        this.userRepository = userRepository;
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
        this.reactionRepository = reactionRepository;
        this.whatsAppAuthRepository = whatsAppAuthRepository;
        this.whatsAppManager = whatsAppManager;
    }

    /**
     * GET /api/accounts - List accounts based on user role
     */
    @GetMapping
    public ResponseEntity<?> getAccounts(HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        String companyId = (String) request.getAttribute("userCompanyId");
        String userId = (String) request.getAttribute("userId");

        List<Account> accounts;

        if ("superadmin".equals(role)) {
            accounts = accountRepository.findAll();
        } else if ("admin".equals(role) || "manager".equals(role)) {
            accounts = accountRepository.findByCompanyIdOrderByCompanyOrderAsc(companyId);
        } else {
            // Regular user - only show assigned accounts
            List<UserAccount> userAccounts = userAccountRepository.findByUserIdOrderByOrderAsc(userId);
            List<String> accountIds = userAccounts.stream()
                    .map(UserAccount::getAccountId)
                    .collect(Collectors.toList());
            accounts = accountRepository.findByIdIn(accountIds);
        }

        return ResponseEntity.ok(accounts);
    }

    /**
     * POST /api/accounts - Create a new account
     */
    @PostMapping
    public ResponseEntity<?> createAccount(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        String userCompanyId = (String) request.getAttribute("userCompanyId");

        if (!"superadmin".equals(role) && !"admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        Account account = new Account();
        account.setName((String) body.get("name"));
        account.setType((String) body.getOrDefault("type", "whatsapp"));
        account.setStatus("disconnected");
        account.setIsActive(true);

        String companyId = (String) body.get("companyId");
        if ("superadmin".equals(role) && companyId != null) {
            account.setCompanyId(companyId);
        } else {
            account.setCompanyId(userCompanyId);
        }

        Integer companyOrder = body.get("companyOrder") != null ?
                Integer.parseInt(body.get("companyOrder").toString()) : 0;
        account.setCompanyOrder(companyOrder);

        Account saved = accountRepository.save(account);
        logger.info("Account created: {} (type: {})", saved.getName(), saved.getType());

        return ResponseEntity.status(201).body(saved);
    }

    /**
     * PATCH /api/accounts/:id - Update account
     */
    @PatchMapping("/{id}")
    public ResponseEntity<?> updateAccount(@PathVariable String id, @RequestBody Map<String, Object> body,
                                           HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        if (!"superadmin".equals(role) && !"admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        Optional<Account> accountOpt = accountRepository.findById(id);
        if (accountOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Account not found"));
        }

        Account account = accountOpt.get();

        if (body.containsKey("name")) account.setName((String) body.get("name"));
        if (body.containsKey("isActive")) account.setIsActive((Boolean) body.get("isActive"));
        if (body.containsKey("companyId")) account.setCompanyId((String) body.get("companyId"));
        if (body.containsKey("companyOrder"))
            account.setCompanyOrder(Integer.parseInt(body.get("companyOrder").toString()));
        if (body.containsKey("notes")) account.setNotes((String) body.get("notes"));

        Account updated = accountRepository.save(account);
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/accounts/:id - Soft delete account
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAccount(@PathVariable String id, HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        if (!"superadmin".equals(role) && !"admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        Optional<Account> accountOpt = accountRepository.findById(id);
        if (accountOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Account not found"));
        }

        // Stop WhatsApp connection if running
        whatsAppManager.stopConnection(id);

        // Remove user-account associations
        userAccountRepository.deleteByAccountId(id);

        accountRepository.deleteById(id);
        logger.info("Account deleted: {}", id);

        return ResponseEntity.ok(Map.of("message", "Account deleted successfully"));
    }

    /**
     * DELETE /api/accounts/:id/cleanup - Full cleanup of account data
     */
    @DeleteMapping("/{id}/cleanup")
    public ResponseEntity<?> cleanupAccount(@PathVariable String id, HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        if (!"superadmin".equals(role) && !"admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        whatsAppManager.stopConnection(id);
        chatRepository.deleteByAccountId(id);
        messageRepository.deleteByAccountId(id);
        reactionRepository.deleteByAccountId(id);
        whatsAppAuthRepository.deleteByAccountId(id);
        userAccountRepository.deleteByAccountId(id);
        accountRepository.deleteById(id);

        logger.info("Account fully cleaned up: {}", id);
        return ResponseEntity.ok(Map.of("message", "Account and all data cleaned up"));
    }

    /**
     * POST /api/accounts/:id/qr - Request QR code for WhatsApp connection
     */
    @PostMapping("/{id}/qr")
    public ResponseEntity<?> requestQR(@PathVariable String id, HttpServletRequest request) {
        Optional<Account> accountOpt = accountRepository.findById(id);
        if (accountOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Account not found"));
        }

        Account account = accountOpt.get();
        String userId = (String) request.getAttribute("userId");

        try {
            whatsAppManager.startConnection(account.getId(), userId);
            return ResponseEntity.ok(Map.of("message", "QR code generation started"));
        } catch (Exception e) {
            logger.error("Failed to start QR code generation for account {}: {}", id, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("message", "Failed to start connection: " + e.getMessage()));
        }
    }

    /**
     * PATCH /api/accounts/:id/status - Toggle account status
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> toggleStatus(@PathVariable String id, @RequestBody Map<String, Object> body,
                                          HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        if (!"superadmin".equals(role) && !"admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        Optional<Account> accountOpt = accountRepository.findById(id);
        if (accountOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Account not found"));
        }

        Account account = accountOpt.get();
        Boolean isActive = (Boolean) body.get("isActive");
        account.setIsActive(isActive);

        if (!isActive) {
            whatsAppManager.stopConnection(id);
            account.setStatus("disconnected");
        }

        Account updated = accountRepository.save(account);
        return ResponseEntity.ok(updated);
    }

    /**
     * PUT /api/accounts/:id/assign - Assign account to users
     */
    @PutMapping("/{id}/assign")
    public ResponseEntity<?> assignUsers(@PathVariable String id, @RequestBody Map<String, Object> body,
                                         HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        if (!"superadmin".equals(role) && !"admin".equals(role) && !"manager".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        Optional<Account> accountOpt = accountRepository.findById(id);
        if (accountOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Account not found"));
        }

        // Remove existing assignments
        userAccountRepository.deleteByAccountId(id);

        @SuppressWarnings("unchecked")
        List<String> userIds = (List<String>) body.get("userIds");
        if (userIds != null) {
            for (int i = 0; i < userIds.size(); i++) {
                UserAccount ua = new UserAccount();
                ua.setUserId(userIds.get(i));
                ua.setAccountId(id);
                ua.setOrder(i);
                userAccountRepository.save(ua);
            }
        }

        return ResponseEntity.ok(Map.of("message", "Users assigned successfully"));
    }

    /**
     * PUT /api/accounts/:id/company-order - Update company order
     */
    @PutMapping("/{id}/company-order")
    public ResponseEntity<?> updateCompanyOrder(@PathVariable String id, @RequestBody Map<String, Object> body,
                                                HttpServletRequest request) {
        Optional<Account> accountOpt = accountRepository.findById(id);
        if (accountOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Account not found"));
        }

        Account account = accountOpt.get();
        account.setCompanyOrder(Integer.parseInt(body.get("companyOrder").toString()));
        accountRepository.save(account);

        return ResponseEntity.ok(Map.of("message", "Company order updated"));
    }
}
