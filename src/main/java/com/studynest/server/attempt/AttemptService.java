package com.studynest.server.attempt;

import com.studynest.server.common.CurrentUser;
import com.studynest.server.common.DbRows;
import com.studynest.server.common.exception.ApiException;
import com.studynest.server.quiz.QuizService;
import com.studynest.server.user.entity.User;
import com.studynest.server.user.entity.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AttemptService {
    private static final String ATTEMPT = "SELECT a.id, a.quiz_id AS quizId, a.student_id AS studentId, a.assignment_id AS assignmentId, "
            + "a.attempt_no AS attemptNo, a.status, a.score, a.max_score AS maxScore, a.started_at AS startedAt, "
            + "a.submitted_at AS submittedAt, q.title AS quizTitle, q.time_limit_minutes AS timeLimitMinutes, "
            + "q.result_policy AS resultPolicy, q.lesson_id AS lessonId, q.topic_id AS topicId, t.name AS topicName "
            + "FROM quiz_attempts a JOIN quizzes q ON q.id=a.quiz_id JOIN topics t ON t.id=q.topic_id WHERE a.id=?";
    private final DbRows db;
    private final CurrentUser currentUser;
    private final QuizService quizzes;

    public AttemptService(DbRows db, CurrentUser currentUser, QuizService quizzes) {
        this.db = db; this.currentUser = currentUser; this.quizzes = quizzes;
    }

    @Transactional
    public Map<String,Object> start(Authentication auth, long quizId) {
        User student = currentUser.require(auth, UserRole.STUDENT);
        QuizService.Access access = quizzes.access(student, quizId, false);
        db.one("SELECT id FROM quizzes WHERE id=? FOR UPDATE", quizId);
        Map<String,Object> existing = db.optional(ATTEMPT.replace("WHERE a.id=?",
                "WHERE a.quiz_id=? AND a.student_id=? AND a.status='IN_PROGRESS' ORDER BY a.attempt_no DESC LIMIT 1 FOR UPDATE"),
                quizId, student.getId());
        if (existing != null) {
            if (expired(existing)) {
                finishLocked(existing);
                if (access.assignment() != null && access.assignment().get("dueAt") instanceof Instant due
                        && !due.isAfter(Instant.now())) return viewData(row(number(existing, "id"), false));
            } else return viewData(existing);
        }
        Map<String,Object> quiz = access.quiz();
        if (access.assignment() != null && access.assignment().get("dueAt") instanceof Instant due && !due.isAfter(Instant.now()))
            throw ApiException.conflict("The quiz deadline has passed");
        int used = ((Number) db.one("SELECT COUNT(*) AS total FROM quiz_attempts WHERE quiz_id=? AND student_id=?",
                quizId, student.getId()).get("total")).intValue();
        if (used >= number(quiz, "maxAttempts")) {
            if (existing != null) return viewData(row(number(existing, "id"), false));
            throw ApiException.conflict("Maximum attempts reached");
        }
        BigDecimal max = (BigDecimal) db.one("SELECT COALESCE(SUM(points),0) AS total FROM quiz_questions WHERE quiz_id=?", quizId).get("total");
        if (max.signum() <= 0) throw ApiException.conflict("Quiz has no questions");
        long id = db.insert("INSERT INTO quiz_attempts (quiz_id,student_id,assignment_id,attempt_no,status,max_score) "
                + "VALUES (?,?,?,?,'IN_PROGRESS',?)", quizId, student.getId(),
                access.assignment() == null ? null : access.assignment().get("id"), used + 1, max);
        return viewData(row(id, false));
    }

    @Transactional
    public Map<String,Object> view(Authentication auth, long id) {
        User student = currentUser.require(auth, UserRole.STUDENT);
        Map<String,Object> attempt = owned(student, id, true);
        if (expired(attempt)) finishLocked(attempt);
        return viewData(row(id, false));
    }

    @Transactional
    public Map<String,Object> saveAnswer(Authentication auth, long attemptId, long questionId, List<Long> optionIds) {
        User student = currentUser.require(auth, UserRole.STUDENT);
        Map<String,Object> attempt = owned(student, attemptId, true);
        if ("SUBMITTED".equals(attempt.get("status"))) return Map.of("status", "SUBMITTED");
        if (expired(attempt)) { finishLocked(attempt); return Map.of("status", "SUBMITTED"); }
        if (optionIds == null || optionIds.size() != 1 || optionIds.getFirst() == null)
            throw ApiException.badRequest("Choose exactly one option");
        if (!db.exists("SELECT COUNT(*) FROM quiz_questions WHERE id=? AND quiz_id=?", questionId, attempt.get("quizId")))
            throw ApiException.badRequest("Question is not in this quiz");
        long optionId = optionIds.getFirst();
        if (!db.exists("SELECT COUNT(*) FROM quiz_question_options WHERE id=? AND quiz_question_id=?", optionId, questionId))
            throw ApiException.badRequest("Option is not in this question");
        Map<String,Object> answer = db.optional("SELECT id FROM attempt_answers WHERE attempt_id=? AND quiz_question_id=?", attemptId, questionId);
        long answerId;
        if (answer == null) answerId = db.insert("INSERT INTO attempt_answers (attempt_id,quiz_question_id) VALUES (?,?)", attemptId, questionId);
        else {
            answerId = number(answer, "id");
            db.update("UPDATE attempt_answers SET answered_at=CURRENT_TIMESTAMP(6) WHERE id=?", answerId);
        }
        db.update("DELETE FROM attempt_answer_options WHERE attempt_answer_id=?", answerId);
        db.insert("INSERT INTO attempt_answer_options (attempt_answer_id,quiz_question_option_id) VALUES (?,?)", answerId, optionId);
        return Map.of("status", "IN_PROGRESS", "quizQuestionId", questionId, "selectedOptionIds", List.of(optionId));
    }

    @Transactional
    public Map<String,Object> setFlag(Authentication auth, long attemptId, long questionId, boolean flagged) {
        User student = currentUser.require(auth, UserRole.STUDENT);
        Map<String,Object> attempt = owned(student, attemptId, true);
        if (expired(attempt)) finishLocked(attempt);
        if (!"IN_PROGRESS".equals(attempt.get("status")) || expired(attempt))
            throw ApiException.conflict("Attempt is already submitted");
        if (!db.exists("SELECT COUNT(*) FROM quiz_questions WHERE id=? AND quiz_id=?", questionId, attempt.get("quizId")))
            throw ApiException.badRequest("Question is not in this quiz");
        if (flagged) db.update("INSERT IGNORE INTO attempt_question_flags (attempt_id,quiz_question_id) VALUES (?,?)", attemptId, questionId);
        else db.update("DELETE FROM attempt_question_flags WHERE attempt_id=? AND quiz_question_id=?", attemptId, questionId);
        return Map.of("quizQuestionId", questionId, "flagged", flagged);
    }

    @Transactional
    public Map<String,Object> submit(Authentication auth, long id) {
        User student = currentUser.require(auth, UserRole.STUDENT);
        Map<String,Object> attempt = owned(student, id, true);
        finishLocked(attempt);
        return resultData(row(id, false));
    }

    @Transactional
    public Map<String,Object> result(Authentication auth, long id) {
        User student = currentUser.require(auth, UserRole.STUDENT);
        Map<String,Object> attempt = owned(student, id, true);
        if (expired(attempt)) finishLocked(attempt);
        return resultData(row(id, false));
    }

    public List<Map<String,Object>> mine(Authentication auth) {
        User student = currentUser.require(auth, UserRole.STUDENT);
        return db.list("SELECT a.id, a.quiz_id AS quizId, q.title AS quizTitle, a.attempt_no AS attemptNo, "
                + "a.status, a.started_at AS startedAt, a.submitted_at AS submittedAt "
                + "FROM quiz_attempts a JOIN quizzes q ON q.id=a.quiz_id WHERE a.student_id=? ORDER BY a.started_at DESC", student.getId());
    }

    public List<Map<String,Object>> myResults(Authentication auth) {
        User student = currentUser.require(auth, UserRole.STUDENT);
        List<Map<String,Object>> rows = db.list("SELECT a.id, a.quiz_id AS quizId, q.title AS quizTitle, "
                + "a.attempt_no AS attemptNo, a.status, a.assignment_id AS assignmentId, "
                + "a.submitted_at AS submittedAt, q.result_policy AS resultPolicy, "
                + "q.topic_id AS topicId, t.name AS topicName, s.id AS subjectId, s.name AS subjectName, "
                + "a.score, a.max_score AS maxScore, "
                + "(SELECT COUNT(*) FROM quiz_questions qq WHERE qq.quiz_id=q.id) AS questionCount, "
                + "(SELECT COUNT(*) FROM attempt_answers aa WHERE aa.attempt_id=a.id AND aa.is_correct=1) AS correctCount "
                + "FROM quiz_attempts a JOIN quizzes q ON q.id=a.quiz_id "
                + "JOIN topics t ON t.id=q.topic_id JOIN subjects s ON s.id=t.subject_id "
                + "WHERE a.student_id=? AND a.status='SUBMITTED' "
                + "ORDER BY a.submitted_at DESC, a.id DESC", student.getId());
        for (Map<String,Object> row : rows) {
            boolean available = canReveal(row);
            row.put("available", available);
            if (!available) {
                row.remove("score");
                row.remove("maxScore");
                row.remove("correctCount");
            }
            row.remove("assignmentId");
        }
        return rows;
    }

    @Transactional
    public void expireIfNeeded(long id) {
        Map<String,Object> attempt = row(id, true);
        if (expired(attempt)) finishLocked(attempt);
    }

    private Map<String,Object> resultData(Map<String,Object> attempt) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("attemptId", attempt.get("id")); result.put("quizId", attempt.get("quizId"));
        result.put("quizTitle", attempt.get("quizTitle")); result.put("status", attempt.get("status"));
        result.put("resultPolicy", attempt.get("resultPolicy")); result.put("submittedAt", attempt.get("submittedAt"));
        result.put("lessonId", attempt.get("lessonId")); result.put("topicId", attempt.get("topicId"));
        result.put("topicName", attempt.get("topicName"));
        boolean available = "SUBMITTED".equals(attempt.get("status")) && canReveal(attempt);
        result.put("available", available);
        if (!available) return result;
        result.put("score", attempt.get("score")); result.put("maxScore", attempt.get("maxScore"));
        List<Map<String,Object>> questions = db.list("SELECT id, source_question_id AS sourceQuestionId, stem_snapshot AS stem, explanation_snapshot AS explanation, "
                + "points, sort_order AS sortOrder FROM quiz_questions WHERE quiz_id=? ORDER BY sort_order", attempt.get("quizId"));
        for (Map<String,Object> question : questions) {
            long questionId = number(question, "id");
            Map<String,Object> answer = db.optional("SELECT id, is_correct AS isCorrect, points_earned AS pointsEarned "
                    + "FROM attempt_answers WHERE attempt_id=? AND quiz_question_id=?", attempt.get("id"), questionId);
            question.put("isCorrect", answer == null ? false : booleanValue(answer.get("isCorrect")));
            question.put("pointsEarned", answer == null ? BigDecimal.ZERO : answer.get("pointsEarned"));
            question.put("selectedOptionIds", answer == null ? List.of() : db.list("SELECT quiz_question_option_id AS id FROM attempt_answer_options "
                    + "WHERE attempt_answer_id=?", answer.get("id")).stream().map(row -> number(row, "id")).toList());
            question.put("options", db.list("SELECT id, content_snapshot AS content, is_correct AS isCorrect, sort_order AS sortOrder "
                    + "FROM quiz_question_options WHERE quiz_question_id=? ORDER BY sort_order", questionId));
        }
        result.put("questions", questions);
        return result;
    }

    private boolean canReveal(Map<String,Object> attempt) {
        String policy = (String) attempt.get("resultPolicy");
        if ("AFTER_SUBMIT".equals(policy)) return true;
        if (!"AFTER_DUE".equals(policy) || attempt.get("assignmentId") == null) return false;
        Map<String,Object> assignment = db.optional("SELECT due_at AS dueAt FROM class_assignments WHERE id=?", attempt.get("assignmentId"));
        return assignment != null && assignment.get("dueAt") instanceof Instant due && !due.isAfter(Instant.now());
    }

    private Map<String,Object> viewData(Map<String,Object> attempt) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (String key : List.of("id", "quizId", "quizTitle", "attemptNo", "status", "startedAt", "submittedAt"))
            result.put(key, attempt.get(key));
        result.put("deadlineAt", deadline(attempt));
        List<Map<String,Object>> snapshot = db.list("SELECT id, stem_snapshot AS stem, points, sort_order AS sortOrder "
                + "FROM quiz_questions WHERE quiz_id=? ORDER BY sort_order", attempt.get("quizId"));
        for (Map<String,Object> question : snapshot) {
            long questionId = number(question, "id");
            question.put("options", db.list("SELECT id, content_snapshot AS content, sort_order AS sortOrder "
                    + "FROM quiz_question_options WHERE quiz_question_id=? ORDER BY sort_order", questionId));
            question.put("selectedOptionIds", db.list("SELECT ao.quiz_question_option_id AS id FROM attempt_answer_options ao "
                    + "JOIN attempt_answers a ON a.id=ao.attempt_answer_id "
                    + "WHERE a.attempt_id=? AND a.quiz_question_id=?", attempt.get("id"), questionId)
                    .stream().map(row -> number(row, "id")).toList());
            question.put("flagged", db.exists("SELECT COUNT(*) FROM attempt_question_flags WHERE attempt_id=? AND quiz_question_id=?",
                    attempt.get("id"), questionId));
        }
        result.put("questions", snapshot);
        return result;
    }

    private void finishLocked(Map<String,Object> attempt) {
        if (!"IN_PROGRESS".equals(attempt.get("status"))) return;
        BigDecimal score = BigDecimal.ZERO;
        List<Map<String,Object>> questions = db.list("SELECT id, points FROM quiz_questions WHERE quiz_id=?", attempt.get("quizId"));
        for (Map<String,Object> question : questions) {
            long questionId = number(question, "id");
            Map<String,Object> answer = db.optional("SELECT id FROM attempt_answers WHERE attempt_id=? AND quiz_question_id=?",
                    attempt.get("id"), questionId);
            if (answer == null) continue;
            List<Map<String,Object>> selected = db.list("SELECT quiz_question_option_id AS id FROM attempt_answer_options WHERE attempt_answer_id=?", answer.get("id"));
            Map<String,Object> correct = db.one("SELECT id FROM quiz_question_options WHERE quiz_question_id=? AND is_correct=1", questionId);
            boolean matches = selected.size() == 1 && number(selected.getFirst(), "id") == number(correct, "id");
            BigDecimal earned = matches ? (BigDecimal) question.get("points") : BigDecimal.ZERO;
            db.update("UPDATE attempt_answers SET is_correct=?,points_earned=? WHERE id=?", matches, earned, answer.get("id"));
            score = score.add(earned);
        }
        db.update("UPDATE quiz_attempts SET status='SUBMITTED',score=?,submitted_at=CURRENT_TIMESTAMP(6) WHERE id=? AND status='IN_PROGRESS'",
                score, attempt.get("id"));
    }

    private boolean expired(Map<String,Object> attempt) {
        return "IN_PROGRESS".equals(attempt.get("status")) &&
                deadline(attempt) instanceof Instant due && !due.isAfter(Instant.now());
    }

    private Instant deadline(Map<String,Object> attempt) {
        Instant due = null;
        if (attempt.get("timeLimitMinutes") instanceof Number minutes)
            due = ((Instant) attempt.get("startedAt")).plus(minutes.longValue(), ChronoUnit.MINUTES);
        if (attempt.get("assignmentId") != null) {
            Map<String,Object> assignment = db.optional("SELECT due_at AS dueAt FROM class_assignments WHERE id=?", attempt.get("assignmentId"));
            if (assignment != null && assignment.get("dueAt") instanceof Instant classDue && (due == null || classDue.isBefore(due)))
                due = classDue;
        }
        return due;
    }

    private Map<String,Object> owned(User student, long id, boolean lock) {
        Map<String,Object> attempt = row(id, lock);
        if (number(attempt, "studentId") != student.getId()) throw ApiException.forbidden();
        return attempt;
    }
    private Map<String,Object> row(long id, boolean lock) { return db.one(ATTEMPT + (lock ? " FOR UPDATE" : ""), id); }
    private static long number(Map<String,Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private static boolean booleanValue(Object value) { return Boolean.TRUE.equals(value) || (value instanceof Number number && number.intValue() == 1); }
}
