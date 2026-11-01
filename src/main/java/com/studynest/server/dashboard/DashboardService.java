package com.studynest.server.dashboard;

import com.studynest.server.common.CurrentUser;
import com.studynest.server.common.DbRows;
import com.studynest.server.lesson.LessonService;
import com.studynest.server.user.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DashboardService {
    private final DbRows db;
    private final CurrentUser currentUser;
    private final LessonService lessons;

    public DashboardService(DbRows db, CurrentUser currentUser, LessonService lessons) {
        this.db = db;
        this.currentUser = currentUser;
        this.lessons = lessons;
    }

    public Map<String, Object> dashboard(Authentication auth) {
        User user = currentUser.get(auth);
        return switch (user.getRoleCode()) {
            case STUDENT -> student(auth, user.getId());
            case TEACHER -> teacher(user.getId());
            case ADMIN -> admin();
        };
    }

    private Map<String, Object> student(Authentication auth, long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", "STUDENT");
        result.put("summary", db.one("SELECT "
                + "(SELECT COUNT(*) FROM class_members WHERE student_id=? AND status='ACTIVE') AS classCount, "
                + "(SELECT COUNT(*) FROM lesson_completions WHERE student_id=?) AS completedLessons, "
                + "(SELECT COUNT(*) FROM quiz_attempts WHERE student_id=? AND status='SUBMITTED') AS submittedAttempts, "
                + "(SELECT ROUND(AVG(CASE WHEN a.max_score>0 AND (q.result_policy='AFTER_SUBMIT' "
                + "OR (q.result_policy='AFTER_DUE' AND ca.due_at<=CURRENT_TIMESTAMP(6))) "
                + "THEN a.score/a.max_score*100 END),1) FROM quiz_attempts a "
                + "JOIN quizzes q ON q.id=a.quiz_id "
                + "LEFT JOIN class_assignments ca ON ca.id=a.assignment_id "
                + "WHERE a.student_id=? AND a.status='SUBMITTED') AS averagePercent", id, id, id, id));
        Set<Long> completed = db.list("SELECT lesson_id AS lessonId FROM lesson_completions WHERE student_id=?", id)
                .stream().map(row -> ((Number) row.get("lessonId")).longValue()).collect(Collectors.toSet());
        List<Map<String, Object>> suggested = lessons.studentLessons(auth, null).stream()
                .filter(row -> !completed.contains(((Number) row.get("id")).longValue())).limit(4).toList();
        result.put("suggestedLessons", suggested);
        result.put("recentAttempts", db.list("SELECT a.id, a.quiz_id AS quizId, q.title AS quizTitle, "
                + "a.status, a.submitted_at AS submittedAt, a.started_at AS startedAt "
                + "FROM quiz_attempts a JOIN quizzes q ON q.id=a.quiz_id WHERE a.student_id=? "
                + "ORDER BY a.started_at DESC LIMIT 5", id));
        result.put("activityDays", db.list("SELECT activity_day AS day FROM ("
                + "SELECT DATE(completed_at) AS activity_day FROM lesson_completions WHERE student_id=? "
                + "UNION SELECT DATE(submitted_at) AS activity_day FROM quiz_attempts "
                + "WHERE student_id=? AND status='SUBMITTED') days ORDER BY activity_day DESC LIMIT 30", id, id));
        return result;
    }

    private Map<String, Object> teacher(long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", "TEACHER");
        result.put("summary", db.one("SELECT "
                + "(SELECT COUNT(*) FROM classes WHERE teacher_id=? AND status='ACTIVE') AS classCount, "
                + "(SELECT COUNT(*) FROM lessons WHERE teacher_id=? AND status='PUBLISHED') AS publishedLessons, "
                + "(SELECT COUNT(*) FROM quizzes WHERE teacher_id=? AND status='PUBLISHED') AS publishedQuizzes, "
                + "(SELECT COUNT(*) FROM questions WHERE teacher_id=? AND status<>'ARCHIVED') AS questionCount", id, id, id, id));
        result.put("classes", db.list("SELECT c.id, c.name, c.join_code AS joinCode, "
                + "(SELECT COUNT(*) FROM class_members m WHERE m.class_id=c.id AND m.status='ACTIVE') AS studentCount "
                + "FROM classes c WHERE c.teacher_id=? AND c.status='ACTIVE' ORDER BY c.created_at DESC LIMIT 4", id));
        result.put("lessons", db.list("SELECT id, title, status, updated_at AS updatedAt FROM lessons "
                + "WHERE teacher_id=? ORDER BY updated_at DESC LIMIT 4", id));
        result.put("quizzes", db.list("SELECT id, title, status, created_at AS createdAt FROM quizzes "
                + "WHERE teacher_id=? ORDER BY created_at DESC LIMIT 4", id));
        return result;
    }

    private Map<String, Object> admin() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", "ADMIN");
        result.put("summary", db.one("SELECT "
                + "(SELECT COUNT(*) FROM users WHERE status='ACTIVE') AS userCount, "
                + "(SELECT COUNT(*) FROM classes WHERE status='ACTIVE') AS classCount, "
                + "(SELECT COUNT(*) FROM teacher_applications WHERE status='PENDING') AS pendingApplications, "
                + "(SELECT COUNT(*) FROM content_reports WHERE status='PENDING') AS pendingReports"));
        result.put("applications", db.list("SELECT a.id, u.full_name AS fullName, u.email, a.created_at AS createdAt "
                + "FROM teacher_applications a JOIN users u ON u.id=a.user_id "
                + "WHERE a.status='PENDING' ORDER BY a.created_at DESC LIMIT 5"));
        result.put("reports", db.list("SELECT id, target_type AS targetType, target_id AS targetId, reason, "
                + "created_at AS createdAt FROM content_reports WHERE status='PENDING' "
                + "ORDER BY created_at DESC LIMIT 5"));
        result.put("activity", db.list("SELECT DATE(created_at) AS day, COUNT(*) AS total FROM audit_events "
                + "WHERE created_at >= CURRENT_DATE - INTERVAL 6 DAY GROUP BY DATE(created_at) ORDER BY day"));
        return result;
    }
}
