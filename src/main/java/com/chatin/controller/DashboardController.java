package com.chatin.controller;

import com.chatin.model.Account;
import com.chatin.model.Company;
import com.chatin.model.User;
import com.chatin.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;

    public DashboardController(CompanyRepository companyRepository, UserRepository userRepository,
                               AccountRepository accountRepository, ChatRepository chatRepository,
                               MessageRepository messageRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * GET /api/dashboard/overview - Dashboard overview based on role
     */
    @GetMapping("/overview")
    public ResponseEntity<?> getOverview(HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");

        if ("superadmin".equals(role)) {
            return ResponseEntity.ok(Map.of("success", true, "statistics", getSuperAdminOverview()));
        }
        return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
    }

    @GetMapping({"/admin/overview", "/manager/overview"})
    public ResponseEntity<?> getRoleOverview(HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        String companyId = (String) request.getAttribute("userCompanyId");

        if ("admin".equals(role) || "manager".equals(role)) {
            return ResponseEntity.ok(Map.of("success", true, "statistics", getCompanyOverview(companyId)));
        }

        return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
    }

    /**
     * GET /api/dashboard/health - Health status
     */
    @GetMapping("/health")
    public ResponseEntity<?> getHealth(HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");

        if ("superadmin".equals(role)) {
            return ResponseEntity.ok(Map.of("success", true, "health", getAllHealthStatus()));
        }
        return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
    }

    @GetMapping({"/admin/health", "/manager/health"})
    public ResponseEntity<?> getRoleHealth(HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        String companyId = (String) request.getAttribute("userCompanyId");

        if ("admin".equals(role) || "manager".equals(role)) {
            return ResponseEntity.ok(Map.of("success", true, "health", getCompanyHealthStatus(companyId)));
        }

        return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
    }

    private Map<String, Object> getSuperAdminOverview() {
        Map<String, Object> statistics = new HashMap<>();

        long totalCompanies = companyRepository.count();
        List<Company> allCompanies = companyRepository.findAll();
        long activeCompanies = allCompanies.stream().filter(c -> c.getIsActive() != null && c.getIsActive()).count();
        statistics.put("companies", Map.of("total", totalCompanies, "active", activeCompanies));

        long totalUsers = userRepository.count();
        long adminCount = userRepository.countByRole("admin") + userRepository.countByRole("superadmin");
        statistics.put("users", Map.of(
            "total", totalUsers, 
            "admins", adminCount,
            "managers", userRepository.countByRole("manager"),
            "regularUsers", userRepository.countByRole("user")
        ));

        List<Account> allAccounts = accountRepository.findAll();
        long connectedCount = allAccounts.stream().filter(a -> "connected".equals(a.getStatus())).count();
        long disconnectedCount = allAccounts.stream().filter(a -> "disconnected".equals(a.getStatus())).count();
        int connectionRate = allAccounts.isEmpty() ? 0 : (int) ((connectedCount * 100) / allAccounts.size());
        statistics.put("accounts", Map.of(
            "total", allAccounts.size(), 
            "connected", connectedCount, 
            "disconnected", disconnectedCount,
            "connectionRate", connectionRate
        ));

        Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
        long todayMessages = 0;
        long totalMessages = 0;
        long totalChats = 0;
        for (Account acc : allAccounts) {
            totalChats += chatRepository.countByAccountId(acc.getId());
            totalMessages += messageRepository.countByAccountId(acc.getId());
            todayMessages += messageRepository.countByAccountIdAndMessageTimestampAfter(acc.getId(), oneDayAgo);
        }
        statistics.put("messaging", Map.of(
            "totalChats", totalChats,
            "totalMessages", totalMessages,
            "todayMessages", todayMessages
        ));

        allCompanies.sort((c1, c2) -> {
            if (c1.getCreatedAt() == null) return 1;
            if (c2.getCreatedAt() == null) return -1;
            return c2.getCreatedAt().compareTo(c1.getCreatedAt());
        });
        
        List<Map<String, String>> topCompanies = new ArrayList<>();
        int limit = Math.min(allCompanies.size(), 5);
        for(int i = 0; i < limit; i++) {
            Company c = allCompanies.get(i);
            Map<String, String> cMap = new HashMap<>();
            cMap.put("_id", c.getId());
            cMap.put("companyName", c.getName());
            cMap.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
            topCompanies.add(cMap);
        }
        statistics.put("topCompanies", topCompanies);

        return statistics;
    }

    private Map<String, Object> getCompanyOverview(String companyId) {
        Map<String, Object> statistics = new HashMap<>();

        statistics.put("companies", Map.of("total", 1, "active", 1));

        long userCount = userRepository.countByCompanyId(companyId);
        List<User> companyUsers = userRepository.findByCompanyId(companyId);
        long adminCount = companyUsers.stream().filter(u -> "admin".equals(u.getRole())).count();
        long managerCount = companyUsers.stream().filter(u -> "manager".equals(u.getRole())).count();
        long regularCount = companyUsers.stream().filter(u -> "user".equals(u.getRole())).count();
        
        statistics.put("users", Map.of(
            "total", userCount, 
            "admins", adminCount,
            "managers", managerCount,
            "regularUsers", regularCount
        ));

        List<Account> accounts = accountRepository.findByCompanyId(companyId);
        long connectedCount = accounts.stream().filter(a -> "connected".equals(a.getStatus())).count();
        long disconnectedCount = accounts.stream().filter(a -> "disconnected".equals(a.getStatus())).count();
        int connectionRate = accounts.isEmpty() ? 0 : (int) ((connectedCount * 100) / accounts.size());
        
        statistics.put("accounts", Map.of(
            "total", accounts.size(), 
            "connected", connectedCount, 
            "disconnected", disconnectedCount,
            "connectionRate", connectionRate
        ));

        Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
        long todayMessages = 0;
        long totalMessages = 0;
        long totalChats = 0;
        for (Account acc : accounts) {
            totalChats += chatRepository.countByAccountId(acc.getId());
            totalMessages += messageRepository.countByAccountId(acc.getId());
            todayMessages += messageRepository.countByAccountIdAndMessageTimestampAfter(acc.getId(), oneDayAgo);
        }
        statistics.put("messaging", Map.of(
            "totalChats", totalChats,
            "totalMessages", totalMessages,
            "todayMessages", todayMessages
        ));

        return statistics;
    }

    private Map<String, Object> getAllHealthStatus() {
        List<Account> accounts = accountRepository.findAll();
        long disconnectedCount = accounts.stream().filter(a -> "disconnected".equals(a.getStatus())).count();
        long needsAttentionCount = accounts.stream().filter(a -> "needs_attention".equals(a.getStatus()) || Boolean.FALSE.equals(a.getIsActive())).count();

        return Map.of(
            "disconnectedAccounts", disconnectedCount,
            "accountsNeedingAttention", needsAttentionCount
        );
    }

    private Map<String, Object> getCompanyHealthStatus(String companyId) {
        List<Account> accounts = accountRepository.findByCompanyId(companyId);
        long disconnectedCount = accounts.stream().filter(a -> "disconnected".equals(a.getStatus())).count();
        long needsAttentionCount = accounts.stream().filter(a -> "needs_attention".equals(a.getStatus()) || Boolean.FALSE.equals(a.getIsActive())).count();

        return Map.of(
            "disconnectedAccounts", disconnectedCount,
            "accountsNeedingAttention", needsAttentionCount
        );
    }
}
