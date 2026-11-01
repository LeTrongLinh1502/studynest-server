package com.studynest.server.attempt;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@PreAuthorize("hasRole('STUDENT')")
public class AttemptController {
    private final AttemptService service;
    public AttemptController(AttemptService service) { this.service = service; }
    public record AnswerRequest(@NotNull List<Long> optionIds) {}
    public record FlagRequest(@NotNull Boolean flagged) {}

    @PostMapping("/api/quizzes/{id}/attempts") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> start(Authentication auth, @PathVariable long id) { return service.start(auth, id); }
    @GetMapping("/api/attempts/my")
    public List<Map<String,Object>> mine(Authentication auth) { return service.mine(auth); }
    @GetMapping("/api/attempts/my/results")
    public List<Map<String,Object>> myResults(Authentication auth) { return service.myResults(auth); }
    @GetMapping("/api/attempts/{id}")
    public Map<String,Object> view(Authentication auth, @PathVariable long id) { return service.view(auth, id); }
    @PutMapping("/api/attempts/{id}/answers/{questionId}")
    public Map<String,Object> answer(Authentication auth, @PathVariable long id, @PathVariable long questionId,
                                     @Valid @RequestBody AnswerRequest request) {
        return service.saveAnswer(auth, id, questionId, request.optionIds());
    }
    @PutMapping("/api/attempts/{id}/flags/{questionId}")
    public Map<String,Object> flag(Authentication auth, @PathVariable long id, @PathVariable long questionId,
                                   @Valid @RequestBody FlagRequest request) {
        return service.setFlag(auth, id, questionId, request.flagged());
    }
    @PostMapping("/api/attempts/{id}/submit")
    public Map<String,Object> submit(Authentication auth, @PathVariable long id) { return service.submit(auth, id); }
    @GetMapping("/api/attempts/{id}/result")
    public Map<String,Object> result(Authentication auth, @PathVariable long id) { return service.result(auth, id); }
}
