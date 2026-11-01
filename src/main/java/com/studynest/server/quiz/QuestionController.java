package com.studynest.server.quiz;

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
@RequestMapping("/api/teacher/questions")
@PreAuthorize("hasRole('TEACHER')")
public class QuestionController {
    private final QuestionService service;
    public QuestionController(QuestionService service) { this.service = service; }

    public enum Difficulty { EASY, MEDIUM, HARD }
    public record OptionRequest(@NotBlank String content, boolean isCorrect) {}
    public record QuestionRequest(@NotNull Long topicId, @NotNull Difficulty difficulty,
                                  @NotBlank String stem, String explanation,
                                  @NotNull @Size(min=2,max=8) List<@Valid OptionRequest> options) {}
    public record BatchQuestionItem(@NotNull Difficulty difficulty, @NotBlank String stem,
                                    String explanation,
                                    @NotNull @Size(min=2,max=8) List<@Valid OptionRequest> options) {}
    public record BatchQuestionRequest(@NotNull Long topicId,
                                       @NotNull @Size(min=1,max=20) List<@Valid BatchQuestionItem> questions) {}

    @GetMapping
    public List<Map<String,Object>> mine(Authentication auth, @RequestParam(required=false) Long topicId) { return service.mine(auth, topicId); }
    @GetMapping("/{id}")
    public Map<String,Object> one(Authentication auth, @PathVariable long id) { return service.one(auth, id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(Authentication auth, @Valid @RequestBody QuestionRequest request) { return service.create(auth, request); }
    @PostMapping("/batch") @ResponseStatus(HttpStatus.CREATED)
    public List<Map<String,Object>> createBatch(Authentication auth, @Valid @RequestBody BatchQuestionRequest request) {
        return service.createBatch(auth, request);
    }
    @PutMapping("/{id}")
    public Map<String,Object> update(Authentication auth, @PathVariable long id, @Valid @RequestBody QuestionRequest request) { return service.update(auth, id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(Authentication auth, @PathVariable long id) { service.archive(auth, id); }
}
