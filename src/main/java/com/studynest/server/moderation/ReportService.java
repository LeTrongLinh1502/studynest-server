package com.studynest.server.moderation;

import com.studynest.server.common.AuditService;
import com.studynest.server.common.CurrentUser;
import com.studynest.server.common.DbRows;
import com.studynest.server.common.exception.ApiException;
import com.studynest.server.lesson.LessonService;
import com.studynest.server.lesson.ResourceService;
import com.studynest.server.quiz.QuizService;
import com.studynest.server.user.entity.User;
import com.studynest.server.user.entity.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ReportService {
    private static final String SELECT = "SELECT id, reporter_id AS reporterId, target_type AS targetType, target_id AS targetId, "
            + "reason, status, reviewed_by AS reviewedBy, resolution_note AS resolutionNote, "
            + "created_at AS createdAt, reviewed_at AS reviewedAt FROM content_reports WHERE id=?";
    private final DbRows db;
    private final CurrentUser currentUser;
    private final LessonService lessons;
    private final QuizService quizzes;
    private final ResourceService resources;
    private final AuditService audit;

    public ReportService(DbRows db, CurrentUser currentUser, LessonService lessons, QuizService quizzes,
                         ResourceService resources, AuditService audit) {
        this.db = db; this.currentUser = currentUser; this.lessons = lessons;
        this.quizzes = quizzes; this.resources = resources; this.audit = audit;
    }

    @Transactional
    public Map<String,Object> create(Authentication auth, String targetType, long targetId, String reason) {
        User reporter = currentUser.get(auth);
        checkVisible(reporter, auth, targetType, targetId);
        if (db.exists("SELECT COUNT(*) FROM content_reports WHERE reporter_id=? AND target_type=? AND target_id=? AND status='PENDING'",
                reporter.getId(), targetType, targetId)) throw ApiException.conflict("A pending report already exists for this content");
        long id = db.insert("INSERT INTO content_reports (reporter_id,target_type,target_id,reason,status) VALUES (?,?,?,?,'PENDING')",
                reporter.getId(), targetType, targetId, reason.trim());
        return db.one(SELECT, id);
    }

    public List<Map<String,Object>> mine(Authentication auth) {
        User reporter = currentUser.get(auth);
        return db.list("SELECT id, reporter_id AS reporterId, target_type AS targetType, target_id AS targetId, "
                + "reason, status, resolution_note AS resolutionNote, created_at AS createdAt, reviewed_at AS reviewedAt "
                + "FROM content_reports WHERE reporter_id=? ORDER BY created_at DESC", reporter.getId());
    }

    public List<Map<String,Object>> all(String status) {
        if (status != null && !List.of("PENDING", "HIDDEN", "CLOSED").contains(status)) throw ApiException.badRequest("Invalid status");
        String sql = "SELECT r.id, r.reporter_id AS reporterId, u.full_name AS reporterName, u.email AS reporterEmail, "
                + "r.target_type AS targetType, r.target_id AS targetId, r.reason, r.status, "
                + "r.reviewed_by AS reviewedBy, r.resolution_note AS resolutionNote, "
                + "r.created_at AS createdAt, r.reviewed_at AS reviewedAt FROM content_reports r "
                + "JOIN users u ON u.id=r.reporter_id " + (status == null ? "" : "WHERE r.status=? ") + "ORDER BY r.created_at DESC";
        return status == null ? db.list(sql) : db.list(sql, status);
    }

    @Transactional
    public Map<String,Object> review(Authentication auth, long id, String decision, String note) {
        User admin = currentUser.require(auth, UserRole.ADMIN);
        Map<String,Object> report = db.one(SELECT + " FOR UPDATE", id);
        if (!"PENDING".equals(report.get("status"))) throw ApiException.conflict("Report has already been reviewed");
        String type = (String) report.get("targetType");
        long targetId = ((Number) report.get("targetId")).longValue();
        if ("HIDE".equals(decision)) hide(type, targetId);
        else if (!"CLOSE".equals(decision)) throw ApiException.badRequest("Decision must be HIDE or CLOSE");
        db.update("UPDATE content_reports SET status=?,reviewed_by=?,resolution_note=?,reviewed_at=CURRENT_TIMESTAMP(6) WHERE id=?",
                "HIDE".equals(decision) ? "HIDDEN" : "CLOSED", admin.getId(), note == null ? null : note.trim(), id);
        audit.record(admin.getId(), "REPORT_" + decision, "CONTENT_REPORT", id, type + "#" + targetId);
        return db.one(SELECT, id);
    }

    public List<Map<String,Object>> auditEvents() {
        return db.list("SELECT a.id, a.actor_id AS actorId, u.full_name AS actorName, a.action_code AS actionCode, "
                + "a.target_type AS targetType, a.target_id AS targetId, a.details, a.created_at AS createdAt "
                + "FROM audit_events a JOIN users u ON u.id=a.actor_id ORDER BY a.created_at DESC LIMIT 500");
    }

    private void checkVisible(User user, Authentication auth, String type, long id) {
        switch (type) {
            case "LESSON" -> lessons.requireVisible(user, id);
            case "LESSON_RESOURCE" -> {
                Map<String,Object> row = db.one("SELECT lesson_id AS lessonId FROM lesson_resources WHERE id=?", id);
                lessons.requireVisible(user, ((Number) row.get("lessonId")).longValue());
            }
            case "QUIZ" -> {
                if (user.getRoleCode() == UserRole.STUDENT) quizzes.access(user, id, false);
                else if (user.getRoleCode() == UserRole.TEACHER) quizzes.owner(auth, id);
                else quizzes.raw(id);
            }
            case "QUESTION" -> {
                Map<String,Object> question = db.one("SELECT teacher_id AS teacherId FROM questions WHERE id=?", id);
                if (user.getRoleCode() == UserRole.TEACHER && ((Number) question.get("teacherId")).longValue() == user.getId()) return;
                if (user.getRoleCode() == UserRole.ADMIN) return;
                if (user.getRoleCode() != UserRole.STUDENT) throw ApiException.forbidden();
                List<Map<String,Object>> quizIds = db.list("SELECT DISTINCT q.id FROM quizzes q JOIN quiz_questions qq ON qq.quiz_id=q.id "
                        + "WHERE qq.source_question_id=? AND q.status='PUBLISHED'", id);
                for (Map<String,Object> row : quizIds) {
                    try { quizzes.access(user, ((Number) row.get("id")).longValue(), false); return; }
                    catch (ApiException ignored) { }
                }
                throw ApiException.forbidden();
            }
            default -> throw ApiException.badRequest("Unsupported report target");
        }
    }

    private void hide(String type, long id) {
        switch (type) {
            case "QUESTION" -> {
                if (db.update("UPDATE questions SET status='ARCHIVED' WHERE id=?", id) == 0) throw ApiException.notFound("Question not found");
                db.update("UPDATE quizzes SET status='ARCHIVED' WHERE id IN "
                        + "(SELECT quiz_id FROM quiz_questions WHERE source_question_id=?)", id);
                db.update("UPDATE class_assignments SET status='CANCELLED' WHERE target_type='QUIZ' AND target_id IN "
                        + "(SELECT quiz_id FROM quiz_questions WHERE source_question_id=?)", id);
            }
            case "QUIZ" -> {
                if (db.update("UPDATE quizzes SET status='ARCHIVED' WHERE id=?", id) == 0) throw ApiException.notFound("Quiz not found");
                db.update("UPDATE class_assignments SET status='CANCELLED' WHERE target_type='QUIZ' AND target_id=?", id);
            }
            case "LESSON" -> {
                if (db.update("UPDATE lessons SET status='ARCHIVED' WHERE id=?", id) == 0) throw ApiException.notFound("Lesson not found");
                db.update("UPDATE class_assignments SET status='CANCELLED' WHERE target_type='LESSON' AND target_id=?", id);
            }
            case "LESSON_RESOURCE" -> resources.hideForModeration(id);
            default -> throw ApiException.badRequest("Unsupported report target");
        }
    }
}
