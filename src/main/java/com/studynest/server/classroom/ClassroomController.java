package com.studynest.server.classroom;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ClassroomController {
    private final ClassroomService service;
    public ClassroomController(ClassroomService service) { this.service = service; }
    public record ClassRequest(@NotBlank @Size(max=150) String name, @Size(max=5000) String description) {}
    public record JoinRequest(@NotBlank @Size(min=8,max=8) String joinCode) {}

    @GetMapping("/api/teacher/classes") @PreAuthorize("hasRole('TEACHER')")
    public List<Map<String,Object>> teacherClasses(Authentication auth) { return service.mineTeacher(auth); }

    @PostMapping("/api/teacher/classes") @PreAuthorize("hasRole('TEACHER')") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(Authentication auth, @Valid @RequestBody ClassRequest r) { return service.create(auth, r.name(), r.description()); }

    @GetMapping("/api/teacher/classes/{id}") @PreAuthorize("hasRole('TEACHER')")
    public Map<String,Object> one(Authentication auth, @PathVariable long id) { return service.teacherClass(auth, id); }

    @PutMapping("/api/teacher/classes/{id}") @PreAuthorize("hasRole('TEACHER')")
    public Map<String,Object> update(Authentication auth, @PathVariable long id, @Valid @RequestBody ClassRequest r) {
        return service.update(auth, id, r.name(), r.description());
    }

    @DeleteMapping("/api/teacher/classes/{id}") @PreAuthorize("hasRole('TEACHER')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(Authentication auth, @PathVariable long id) { service.archive(auth, id); }

    @GetMapping("/api/teacher/classes/{id}/members") @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public List<Map<String,Object>> members(Authentication auth, @PathVariable long id) { return service.members(auth, id); }

    @DeleteMapping("/api/teacher/classes/{id}/members/{studentId}") @PreAuthorize("hasRole('TEACHER')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(Authentication auth, @PathVariable long id, @PathVariable long studentId) { service.removeMember(auth, id, studentId); }

    @PostMapping("/api/classes/join") @PreAuthorize("hasRole('STUDENT')")
    public Map<String,Object> join(Authentication auth, @Valid @RequestBody JoinRequest r) { return service.join(auth, r.joinCode()); }

    @GetMapping("/api/classes/my") @PreAuthorize("hasRole('STUDENT')")
    public List<Map<String,Object>> mine(Authentication auth) { return service.mineStudent(auth); }
}
