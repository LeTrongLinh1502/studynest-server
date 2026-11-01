package com.studynest.server.lesson;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
public class ResourceController {
    private final ResourceService service;
    public ResourceController(ResourceService service) { this.service = service; }
    public enum LinkType { VIDEO_LINK, EXTERNAL_LINK }
    public record LinkRequest(@NotNull LinkType resourceType, @NotBlank @Size(max=200) String title,
                              @NotBlank @Size(max=2048) String externalUrl) {}
    public record ReorderRequest(@NotBlank @Size(max=200) String title, @Min(0) int sortOrder) {}

    @PostMapping("/api/teacher/lessons/{id}/resources/links") @PreAuthorize("hasRole('TEACHER')") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> link(Authentication auth, @PathVariable long id, @Valid @RequestBody LinkRequest r) {
        return service.addLink(auth, id, r.resourceType().name(), r.title(), r.externalUrl());
    }

    @PostMapping(value="/api/teacher/lessons/{id}/resources/files", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TEACHER')") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> file(Authentication auth, @PathVariable long id, @RequestParam("title") @NotBlank String title,
                                   @RequestPart("file") MultipartFile file) {
        return service.addFile(auth, id, title, file);
    }

    @PatchMapping("/api/teacher/lessons/{lessonId}/resources/{id}") @PreAuthorize("hasRole('TEACHER')")
    public Map<String,Object> reorder(Authentication auth, @PathVariable long lessonId, @PathVariable long id,
                                      @Valid @RequestBody ReorderRequest r) {
        return service.reorder(auth, lessonId, id, r.title(), r.sortOrder());
    }

    @DeleteMapping("/api/teacher/lessons/{lessonId}/resources/{id}") @PreAuthorize("hasRole('TEACHER')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable long lessonId, @PathVariable long id) { service.delete(auth, lessonId, id); }

    @GetMapping("/api/lesson-resources/{id}/download")
    public ResponseEntity<FileSystemResource> download(Authentication auth, @PathVariable long id) { return service.download(auth, id); }

    @GetMapping("/api/lesson-resources/{id}/preview")
    public ResponseEntity<FileSystemResource> preview(Authentication auth, @PathVariable long id) { return service.preview(auth, id); }
}
