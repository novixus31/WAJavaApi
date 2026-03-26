package com.chatin.controller;

import com.chatin.model.Company;
import com.chatin.repository.AccountRepository;
import com.chatin.repository.CompanyRepository;
import com.chatin.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private static final Logger logger = LoggerFactory.getLogger(CompanyController.class);

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    public CompanyController(CompanyRepository companyRepository, UserRepository userRepository,
                             AccountRepository accountRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * GET /api/companies - List all companies (superadmin only)
     */
    @GetMapping
    public ResponseEntity<?> getCompanies(HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        if (!"superadmin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        List<Company> companies = companyRepository.findAll();

        // Enrich with user and account counts
        List<Map<String, Object>> result = new ArrayList<>();
        for (Company company : companies) {
            Map<String, Object> companyMap = new HashMap<>();
            companyMap.put("id", company.getId());
            companyMap.put("companyName", company.getName());
            companyMap.put("domain", company.getDomain());
            companyMap.put("isActive", company.getIsActive());
            companyMap.put("createdAt", company.getCreatedAt());
            companyMap.put("updatedAt", company.getUpdatedAt());
            
            companyMap.put("_count", Map.of(
                "users", userRepository.countByCompanyId(company.getId()),
                "accounts", accountRepository.countByCompanyId(company.getId())
            ));

            result.add(companyMap);
        }

        return ResponseEntity.ok(Map.of("success", true, "companies", result));
    }

    /**
     * GET /api/companies/:id - Get single company
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getCompany(@PathVariable String id, HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        if (!"superadmin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        Optional<Company> companyOpt = companyRepository.findById(id);
        if (companyOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Company not found"));
        }

        return ResponseEntity.ok(companyOpt.get());
    }

    /**
     * POST /api/companies - Create new company
     */
    @PostMapping
    public ResponseEntity<?> createCompany(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        if (!"superadmin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        String name = (String) body.get("companyName");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Company name is required"));
        }

        if (companyRepository.findByName(name).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("message", "Company name already exists"));
        }

        Company company = new Company();
        company.setName(name);
        company.setDomain((String) body.get("domain"));
        company.setIsActive(true);

        Company saved = companyRepository.save(company);
        logger.info("Company created: {}", saved.getName());

        return ResponseEntity.status(201).body(saved);
    }

    /**
     * PUT /api/companies/:id - Update company
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateCompany(@PathVariable String id, @RequestBody Map<String, Object> body,
                                           HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        if (!"superadmin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        Optional<Company> companyOpt = companyRepository.findById(id);
        if (companyOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Company not found"));
        }

        Company company = companyOpt.get();
        if (body.containsKey("companyName")) company.setName((String) body.get("companyName"));
        if (body.containsKey("domain")) company.setDomain((String) body.get("domain"));
        if (body.containsKey("isActive")) company.setIsActive((Boolean) body.get("isActive"));

        Company updated = companyRepository.save(company);
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/companies/:id - Delete company
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCompany(@PathVariable String id, HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        if (!"superadmin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("message", "Not authorized"));
        }

        Optional<Company> companyOpt = companyRepository.findById(id);
        if (companyOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Company not found"));
        }

        // Check if company has users or accounts
        long userCount = userRepository.countByCompanyId(id);
        long accountCount = accountRepository.countByCompanyId(id);
        if (userCount > 0 || accountCount > 0) {
            return ResponseEntity.status(400).body(Map.of(
                    "message", "Cannot delete company with existing users or accounts",
                    "userCount", userCount,
                    "accountCount", accountCount
            ));
        }

        companyRepository.deleteById(id);
        logger.info("Company deleted: {}", id);

        return ResponseEntity.ok(Map.of("message", "Company deleted successfully"));
    }
}
