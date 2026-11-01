package com.studynest.server.teacher;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class TeacherApplicationController {
    private final TeacherApplicationService service;
    public TeacherApplicationController(TeacherApplicationService service) { this.service = service; }

    public record ApplyRequest(@Size(max=2000) String reason) {}
    public record ReviewRequest(@NotNull Decision decision, @Size(max=2000) String reviewNote) {}
    public enum Decision { APPROVED, REJECTED }

    @PostMapping("/api/teacher-applications") @PreAuthorize("hasRole('STUDENT')") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> apply(Authentication auth, @Valid @RequestBody ApplyRequest r) { return service.apply(auth, r.reason()); }

    @GetMapping("/api/teacher-applications/me")
    public List<Map<String,Object>> mine(Authentication auth) { return service.mine(auth); }

    @GetMapping("/api/admin/teacher-applications") @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String,Object>> all(@RequestParam(required=false) String status) { return service.all(status); }

    @PatchMapping("/api/admin/teacher-applications/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> review(Authentication auth, @PathVariable long id, @Valid @RequestBody ReviewRequest r) {
        return service.review(auth, id, r.decision().name(), r.reviewNote());
    }
}
