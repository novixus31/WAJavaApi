package com.chatin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/")
    public Map<String, String> health() {
        return Map.of("message", "ChatIn Backend API is running!");
    }

    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        return Map.of(
                "status", "ok",
                "timestamp", System.currentTimeMillis(),
                "service", "ChatIn Backend (Java)"
        );
    }
}
