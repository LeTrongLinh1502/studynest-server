package com.studynest.server.lesson;

import com.studynest.server.common.exception.ApiException;
import com.studynest.server.quiz.QuizService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
public class LessonController {
    private final LessonService service;
    private final QuizService quizzes;
    public LessonController(LessonService service, QuizService quizzes) { this.service = service; this.quizzes = quizzes; }

    public enum Visibility { PUBLIC, CLASS }
    public record LessonRequest(@NotNull Long topicId, @NotBlank @Size(max=200) String title,
                                @Size(max=5000) String summary, String body, Visibility visibility) {}
    public record AssignmentRequest(Long lessonId, String targetType, Long targetId, Instant availableAt, Instant dueAt) {}
    public record AssignClassesRequest(@NotNull List<@NotNull Long> classIds, Instant availableAt, Instant dueAt) {}

    @GetMapping("/api/teacher/lessons") @PreAuthorize("hasRole('TEACHER')")
    public List<Map<String,Object>> teacherLessons(Authentication auth) { return service.teacherLessons(auth); }

    @PostMapping("/api/teacher/lessons") @PreAuthorize("hasRole('TEACHER')") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(Authentication auth, @Valid @RequestBody LessonRequest r) {
        return service.create(auth, r.topicId(), r.title(), r.summary(), r.body(), visibility(r));
    }

    @GetMapping("/api/teacher/lessons/{id}") @PreAuthorize("hasRole('TEACHER')")
    public Map<String,Object> teacherOne(Authentication auth, @PathVariable long id) { return service.teacherOne(auth, id); }

    @PutMapping("/api/teacher/lessons/{id}") @PreAuthorize("hasRole('TEACHER')")
    public Map<String,Object> update(Authentication auth, @PathVariable long id, @Valid @RequestBody LessonRequest r) {
        return service.update(auth, id, r.topicId(), r.title(), r.summary(), r.body(), visibility(r));
    }

    @PostMapping("/api/teacher/lessons/{id}/publish") @PreAuthorize("hasRole('TEACHER')")
    public Map<String,Object> publish(Authentication auth, @PathVariable long id) { return service.publish(auth, id); }

    @PostMapping("/api/teacher/lessons/{id}/assign-classes") @PreAuthorize("hasRole('TEACHER')")
    public List<Map<String,Object>> assignClasses(Authentication auth, @PathVariable long id,
                                                   @Valid @RequestBody AssignClassesRequest request) {
        return service.assignClasses(auth, id, request.classIds(), request.availableAt(), request.dueAt());
    }

    @DeleteMapping("/api/teacher/lessons/{id}") @PreAuthorize("hasRole('TEACHER')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(Authentication auth, @PathVariable long id) { service.archive(auth, id); }

    @GetMapping("/api/lessons") @PreAuthorize("hasRole('STUDENT')")
    public List<Map<String,Object>> studentLessons(Authentication auth, @RequestParam(required=false) Long topicId) {
        return service.studentLessons(auth, topicId);
    }

    @GetMapping("/api/lessons/{id}") @PreAuthorize("hasRole('STUDENT')")
    public Map<String,Object> studentOne(Authentication auth, @PathVariable long id) { return service.studentOne(auth, id); }

    @PostMapping("/api/lessons/{id}/complete") @PreAuthorize("hasRole('STUDENT')")
    public Map<String,Object> complete(Authentication auth, @PathVariable long id) { return service.complete(auth, id); }

    @PostMapping("/api/teacher/classes/{id}/assignments") @PreAuthorize("hasRole('TEACHER')") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> assign(Authentication auth, @PathVariable long id, @Valid @RequestBody AssignmentRequest r) {
        if (r.lessonId() != null && r.targetType() == null && r.targetId() == null)
            return service.assign(auth, id, r.lessonId(), r.availableAt(), r.dueAt());
        if ("LESSON".equals(r.targetType()) && r.targetId() != null)
            return service.assign(auth, id, r.targetId(), r.availableAt(), r.dueAt());
        if ("QUIZ".equals(r.targetType()) && r.targetId() != null)
            return quizzes.assign(auth, id, r.targetId(), r.availableAt(), r.dueAt());
        throw ApiException.badRequest("Choose one target: lessonId or targetType and targetId");
    }

    @GetMapping("/api/teacher/classes/{id}/assignments") @PreAuthorize("hasRole('TEACHER')")
    public List<Map<String,Object>> assignments(Authentication auth, @PathVariable long id) { return service.classAssignments(auth, id); }

    @DeleteMapping("/api/teacher/classes/{id}/assignments/{assignmentId}") @PreAuthorize("hasRole('TEACHER')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(Authentication auth, @PathVariable long id, @PathVariable long assignmentId) {
        service.cancelAssignment(auth, id, assignmentId);
    }

    @GetMapping("/api/classes/my/assignments") @PreAuthorize("hasRole('STUDENT')")
    public List<Map<String,Object>> studentAssignments(Authentication auth) { return service.studentAssignments(auth); }

    private static String visibility(LessonRequest r) { return r.visibility() == null ? "CLASS" : r.visibility().name(); }
}
