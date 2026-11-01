package com.studynest.server.moderation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service) { this.service = service; }
    public enum TargetType { QUESTION, QUIZ, LESSON, LESSON_RESOURCE }
    public enum Decision { HIDE, CLOSE }
    public record CreateRequest(@NotNull TargetType targetType, @NotNull Long targetId,
                                @NotBlank @Size(min=10,max=2000) String reason) {}
    public record ReviewRequest(@NotNull Decision decision, @Size(max=2000) String resolutionNote) {}

    @PostMapping("/api/content-reports") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(Authentication auth, @Valid @RequestBody CreateRequest request) {
        return service.create(auth, request.targetType().name(), request.targetId(), request.reason());
    }
    @GetMapping("/api/content-reports/my")
    public List<Map<String,Object>> mine(Authentication auth) { return service.mine(auth); }
    @GetMapping("/api/admin/content-reports") @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String,Object>> all(@RequestParam(required=false) String status) { return service.all(status); }
    @PatchMapping("/api/admin/content-reports/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> review(Authentication auth, @PathVariable long id, @Valid @RequestBody ReviewRequest request) {
        return service.review(auth, id, request.decision().name(), request.resolutionNote());
    }
    @GetMapping("/api/admin/audit-events") @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String,Object>> audit() { return service.auditEvents(); }
}
