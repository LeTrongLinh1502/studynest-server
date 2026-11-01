package com.studynest.server.quiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
public class QuizController {
    private final QuizService service;
    public QuizController(QuizService service) { this.service = service; }

    public enum Visibility { PUBLIC, CLASS }
    public enum ResultPolicy { AFTER_SUBMIT, AFTER_DUE, HIDDEN }
    public record SelectedQuestion(@NotNull Long questionId,
                                   @NotNull @DecimalMin("0.01") @Digits(integer=6,fraction=2) BigDecimal points) {}
    public record QuizRequest(@NotNull Long topicId, Long lessonId, @NotBlank @Size(max=200) String title,
                              String description, @Min(1) @Max(180) Integer timeLimitMinutes,
                              @Min(1) @Max(10) int maxAttempts, @NotNull ResultPolicy resultPolicy,
                              @NotNull Visibility visibility,
                              @NotNull @Size(min=1,max=100) List<@Valid SelectedQuestion> questions) {}

    @GetMapping("/api/teacher/quizzes") @PreAuthorize("hasRole('TEACHER')")
    public List<Map<String,Object>> teacherMine(Authentication auth) { return service.teacherMine(auth); }
    @GetMapping("/api/teacher/quizzes/{id}") @PreAuthorize("hasRole('TEACHER')")
    public Map<String,Object> teacherOne(Authentication auth, @PathVariable long id) { return service.teacherOne(auth, id); }
    @PostMapping("/api/teacher/quizzes") @PreAuthorize("hasRole('TEACHER')") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(Authentication auth, @Valid @RequestBody QuizRequest request) { return service.create(auth, request); }
    @PutMapping("/api/teacher/quizzes/{id}") @PreAuthorize("hasRole('TEACHER')")
    public Map<String,Object> update(Authentication auth, @PathVariable long id, @Valid @RequestBody QuizRequest request) { return service.update(auth, id, request); }
    @PostMapping("/api/teacher/quizzes/{id}/publish") @PreAuthorize("hasRole('TEACHER')")
    public Map<String,Object> publish(Authentication auth, @PathVariable long id) { return service.publish(auth, id); }
    @DeleteMapping("/api/teacher/quizzes/{id}") @PreAuthorize("hasRole('TEACHER')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(Authentication auth, @PathVariable long id) { service.archive(auth, id); }
    @GetMapping("/api/quizzes") @PreAuthorize("hasRole('STUDENT')")
    public List<Map<String,Object>> studentList(Authentication auth, @RequestParam(required=false) Long topicId) { return service.studentList(auth, topicId); }
    @GetMapping("/api/quizzes/{id}") @PreAuthorize("hasRole('STUDENT')")
    public Map<String,Object> studentOne(Authentication auth, @PathVariable long id) { return service.studentOne(auth, id); }
    @GetMapping("/api/quizzes/assignments/my") @PreAuthorize("hasRole('STUDENT')")
    public List<Map<String,Object>> studentAssignments(Authentication auth) { return service.studentAssignments(auth); }
}
