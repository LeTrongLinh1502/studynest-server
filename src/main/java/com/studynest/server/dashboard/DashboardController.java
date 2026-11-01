package com.studynest.server.dashboard;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) { this.service = service; }

    @GetMapping("/api/dashboard")
    public Map<String, Object> dashboard(Authentication authentication) {
        return service.dashboard(authentication);
    }
}
