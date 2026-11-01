package com.studynest.server.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class CatalogController {
    private final CatalogService service;
    public CatalogController(CatalogService service) { this.service = service; }

    public record SubjectRequest(@NotBlank @Size(max=50) String code, @NotBlank @Size(max=150) String name) {}
    public record TopicRequest(@NotNull Long subjectId, Long parentTopicId, @NotBlank @Size(max=80) String code,
                               @NotBlank @Size(max=150) String name, @Min(0) int sortOrder) {}

    @GetMapping("/api/subjects")
    public List<Map<String,Object>> subjects() { return service.subjects(false); }

    @GetMapping("/api/subjects/{id}/topics")
    public List<Map<String,Object>> topics(@PathVariable long id) { return service.topics(id, false); }

    @GetMapping("/api/admin/subjects") @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String,Object>> adminSubjects() { return service.subjects(true); }

    @PostMapping("/api/admin/subjects") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createSubject(@Valid @RequestBody SubjectRequest r) { return service.createSubject(r.code(), r.name()); }

    @PutMapping("/api/admin/subjects/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> updateSubject(@PathVariable long id, @Valid @RequestBody SubjectRequest r) { return service.updateSubject(id, r.code(), r.name()); }

    @DeleteMapping("/api/admin/subjects/{id}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveSubject(@PathVariable long id) { service.archiveSubject(id); }

    @GetMapping("/api/admin/subjects/{id}/topics") @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String,Object>> adminTopics(@PathVariable long id) { return service.topics(id, true); }

    @PostMapping("/api/admin/topics") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createTopic(@Valid @RequestBody TopicRequest r) {
        return service.createTopic(r.subjectId(), r.parentTopicId(), r.code(), r.name(), r.sortOrder());
    }

    @PutMapping("/api/admin/topics/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> updateTopic(@PathVariable long id, @Valid @RequestBody TopicRequest r) {
        return service.updateTopic(id, r.subjectId(), r.parentTopicId(), r.code(), r.name(), r.sortOrder());
    }

    @DeleteMapping("/api/admin/topics/{id}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveTopic(@PathVariable long id) { service.archiveTopic(id); }
}
