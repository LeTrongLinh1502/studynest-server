package com.studynest.server.analytics;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@PreAuthorize("hasRole('TEACHER')")
public class AnalyticsController {
    private final AnalyticsService service;
    public AnalyticsController(AnalyticsService service) { this.service = service; }

    @GetMapping("/api/teacher/quizzes/{id}/results")
    public Map<String,Object> quiz(Authentication auth, @PathVariable long id) { return service.quiz(auth, id); }

    @GetMapping("/api/teacher/classes/{id}/analytics")
    public Map<String,Object> classroom(Authentication auth, @PathVariable long id) { return service.classroom(auth, id); }
}
