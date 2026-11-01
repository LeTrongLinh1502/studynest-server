package com.studynest.server.lesson;

import com.studynest.server.catalog.CatalogService;
import com.studynest.server.classroom.ClassroomService;
import com.studynest.server.common.AuditService;
import com.studynest.server.common.CurrentUser;
import com.studynest.server.common.DbRows;
import com.studynest.server.common.exception.ApiException;
import com.studynest.server.user.entity.User;
import com.studynest.server.user.entity.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;

@Service
public class LessonService {
    private static final String FIELDS = "l.id, l.teacher_id AS teacherId, l.topic_id AS topicId, "
            + "(SELECT subject_id FROM topics WHERE topics.id=l.topic_id) AS subjectId, "
            + "(SELECT name FROM topics WHERE topics.id=l.topic_id) AS topicName, "
            + "(SELECT s.name FROM topics t JOIN subjects s ON s.id=t.subject_id WHERE t.id=l.topic_id) AS subjectName, "
            + "(SELECT COUNT(*) FROM lesson_resources r WHERE r.lesson_id=l.id AND r.resource_type='FILE') AS fileCount, "
            + "(SELECT COUNT(*) FROM lesson_resources r WHERE r.lesson_id=l.id AND r.resource_type='VIDEO_LINK') AS videoCount, "
            + "l.title, l.summary, l.body, "
            + "l.visibility, l.status, l.published_at AS publishedAt, l.created_at AS createdAt, l.updated_at AS updatedAt";
    private static final String SELECT = "SELECT " + FIELDS + " FROM lessons l WHERE l.id=?";
    private static final String ASSIGNED = "EXISTS (SELECT 1 FROM class_assignments a "
            + "JOIN classes c ON c.id=a.class_id JOIN class_members m ON m.class_id=a.class_id "
            + "WHERE a.target_type='LESSON' AND a.target_id=l.id AND a.status='ACTIVE' "
            + "AND a.available_at<=CURRENT_TIMESTAMP(6) AND c.status='ACTIVE' "
            + "AND m.student_id=? AND m.status='ACTIVE')";
    private final DbRows db;
    private final CurrentUser currentUser;
    private final CatalogService catalog;
    private final ClassroomService classrooms;
    private final AuditService audit;

    public LessonService(DbRows db, CurrentUser currentUser, CatalogService catalog, ClassroomService classrooms, AuditService audit) {
        this.db = db; this.currentUser = currentUser; this.catalog = catalog; this.classrooms = classrooms; this.audit = audit;
    }

    @Transactional
    public Map<String,Object> create(Authentication auth, long topicId, String title, String summary, String body, String visibility) {
        User teacher = currentUser.require(auth, UserRole.TEACHER);
        catalog.requireActiveTopic(topicId);
        validateVisibility(visibility);
        long id = db.insert("INSERT INTO lessons (teacher_id,topic_id,title,summary,body,visibility,status) "
                + "VALUES (?,?,?,?,?,?,'DRAFT')", teacher.getId(), topicId, title.trim(), trim(summary), body, visibility);
        return teacherOne(auth, id);
    }

    public List<Map<String,Object>> teacherLessons(Authentication auth) {
        User teacher = currentUser.require(auth, UserRole.TEACHER);
        return db.list("SELECT " + FIELDS + " FROM lessons l WHERE l.teacher_id=? ORDER BY l.created_at DESC", teacher.getId());
    }

    public Map<String,Object> teacherOne(Authentication auth, long id) {
        Map<String,Object> lesson = requireOwner(auth, id);
        return withResources(lesson);
    }

    @Transactional
    public Map<String,Object> update(Authentication auth, long id, long topicId, String title, String summary, String body, String visibility) {
        Map<String,Object> lesson = requireOwner(auth, id);
        if ("ARCHIVED".equals(lesson.get("status"))) throw ApiException.conflict("Lesson is archived");
        catalog.requireActiveTopic(topicId);
        validateVisibility(visibility);
        db.update("UPDATE lessons SET topic_id=?,title=?,summary=?,body=?,visibility=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",
                topicId, title.trim(), trim(summary), body, visibility, id);
        return teacherOne(auth, id);
    }

    @Transactional
    public Map<String,Object> publish(Authentication auth, long id) {
        Map<String,Object> lesson = requireOwner(auth, id);
        if ("ARCHIVED".equals(lesson.get("status"))) throw ApiException.conflict("Lesson is archived");
        catalog.requireActiveTopic(((Number) lesson.get("topicId")).longValue());
        if ((lesson.get("body") == null || lesson.get("body").toString().isBlank())
                && !db.exists("SELECT COUNT(*) FROM lesson_resources WHERE lesson_id=?", id))
            throw ApiException.badRequest("Add lesson text or a resource before publishing");
        db.update("UPDATE lessons SET status='PUBLISHED', published_at=COALESCE(published_at,CURRENT_TIMESTAMP(6)), "
                + "updated_at=CURRENT_TIMESTAMP(6) WHERE id=?", id);
        audit.record(currentUser.get(auth).getId(), "LESSON_PUBLISHED", "LESSON", id, null);
        return teacherOne(auth, id);
    }

    @Transactional
    public void archive(Authentication auth, long id) {
        requireOwner(auth, id);
        db.update("UPDATE lessons SET status='ARCHIVED',updated_at=CURRENT_TIMESTAMP(6) WHERE id=?", id);
        db.update("UPDATE class_assignments SET status='CANCELLED' WHERE target_type='LESSON' AND target_id=?", id);
        audit.record(currentUser.get(auth).getId(), "LESSON_ARCHIVED", "LESSON", id, null);
    }

    public List<Map<String,Object>> studentLessons(Authentication auth, Long topicId) {
        User student = currentUser.require(auth, UserRole.STUDENT);
        if (topicId != null) catalog.requireActiveTopic(topicId);
        String sql = "SELECT " + FIELDS + ", "
                + "(SELECT COUNT(*) FROM lesson_completions lc WHERE lc.lesson_id=l.id AND lc.student_id=?) AS completed "
                + "FROM lessons l WHERE l.status='PUBLISHED' "
                + "AND (l.visibility='PUBLIC' OR " + ASSIGNED + ") "
                + (topicId == null ? "" : "AND l.topic_id=? ") + "ORDER BY l.published_at DESC";
        List<Map<String,Object>> rows = topicId == null
                ? db.list(sql, student.getId(), student.getId())
                : db.list(sql, student.getId(), student.getId(), topicId);
        rows.forEach(row -> row.put("completed", ((Number) row.get("completed")).intValue() > 0));
        return rows;
    }

    public Map<String,Object> studentOne(Authentication auth, long id) {
        User student = currentUser.require(auth, UserRole.STUDENT);
        Map<String,Object> lesson = requireVisible(student, id);
        Map<String,Object> result = withResources(lesson);
        result.put("completed", db.exists("SELECT COUNT(*) FROM lesson_completions WHERE lesson_id=? AND student_id=?", id, student.getId()));
        return result;
    }

    @Transactional
    public Map<String,Object> complete(Authentication auth, long id) {
        User student = currentUser.require(auth, UserRole.STUDENT);
        requireVisible(student, id);
        db.update("INSERT IGNORE INTO lesson_completions (lesson_id,student_id) VALUES (?,?)", id, student.getId());
        return db.one("SELECT lesson_id AS lessonId, student_id AS studentId, completed_at AS completedAt "
                + "FROM lesson_completions WHERE lesson_id=? AND student_id=?", id, student.getId());
    }

    @Transactional
    public Map<String,Object> assign(Authentication auth, long classId, long lessonId, Instant availableAt, Instant dueAt) {
        Map<String,Object> classroom = classrooms.requireOwner(auth, classId);
        if (!"ACTIVE".equals(classroom.get("status"))) throw ApiException.conflict("Class is archived");
        Map<String,Object> lesson = requireOwner(auth, lessonId);
        if (!"PUBLISHED".equals(lesson.get("status"))) throw ApiException.conflict("Lesson must be published");
        Instant from = availableAt == null ? Instant.now() : availableAt;
        if (dueAt != null && dueAt.isBefore(from)) throw ApiException.badRequest("dueAt must be after availableAt");
        Map<String,Object> existing = db.optional("SELECT id, status FROM class_assignments WHERE class_id=? AND target_type='LESSON' AND target_id=?",
                classId, lessonId);
        long id;
        if (existing == null) {
            id = db.insert("INSERT INTO class_assignments (class_id,target_type,target_id,assigned_by,available_at,due_at,status) "
                    + "VALUES (?,'LESSON',?,?,?,?,'ACTIVE')", classId, lessonId,
                    ((Number) classroom.get("teacherId")).longValue(), Timestamp.from(from), dueAt == null ? null : Timestamp.from(dueAt));
        } else {
            id = ((Number) existing.get("id")).longValue();
            db.update("UPDATE class_assignments SET assigned_by=?,available_at=?,due_at=?,status='ACTIVE' WHERE id=?",
                    ((Number) classroom.get("teacherId")).longValue(), Timestamp.from(from), dueAt == null ? null : Timestamp.from(dueAt), id);
        }
        return assignment(id);
    }

    @Transactional
    public List<Map<String,Object>> assignClasses(Authentication auth, long lessonId, List<Long> classIds,
                                                   Instant availableAt, Instant dueAt) {
        requireOwner(auth, lessonId);
        if (classIds == null || classIds.isEmpty() || classIds.size() > 50 || classIds.contains(null)
                || new LinkedHashSet<>(classIds).size() != classIds.size())
            throw ApiException.badRequest("Choose 1 to 50 distinct classes");
        return classIds.stream().map(classId -> assign(auth, classId, lessonId, availableAt, dueAt)).toList();
    }

    public List<Map<String,Object>> classAssignments(Authentication auth, long classId) {
        classrooms.requireOwner(auth, classId);
        return db.list("SELECT a.id, a.class_id AS classId, a.target_type AS targetType, a.target_id AS targetId, "
                + "l.title AS lessonTitle, q.title AS quizTitle, a.assigned_by AS assignedBy, a.available_at AS availableAt, "
                + "a.due_at AS dueAt, a.status, a.created_at AS createdAt FROM class_assignments a "
                + "LEFT JOIN lessons l ON l.id=a.target_id AND a.target_type='LESSON' "
                + "LEFT JOIN quizzes q ON q.id=a.target_id AND a.target_type='QUIZ' WHERE a.class_id=? ORDER BY a.created_at DESC", classId);
    }

    public List<Map<String,Object>> studentAssignments(Authentication auth) {
        User student = currentUser.require(auth, UserRole.STUDENT);
        return db.list("SELECT a.id, a.class_id AS classId, c.name AS className, a.target_id AS lessonId, l.title AS lessonTitle, "
                + "a.available_at AS availableAt, a.due_at AS dueAt, a.status, a.created_at AS createdAt "
                + "FROM class_assignments a JOIN classes c ON c.id=a.class_id JOIN class_members m ON m.class_id=c.id "
                + "JOIN lessons l ON l.id=a.target_id AND a.target_type='LESSON' "
                + "WHERE m.student_id=? AND m.status='ACTIVE' AND c.status='ACTIVE' AND a.status='ACTIVE' "
                + "AND l.status='PUBLISHED' ORDER BY a.available_at DESC", student.getId());
    }

    @Transactional
    public void cancelAssignment(Authentication auth, long classId, long assignmentId) {
        classrooms.requireOwner(auth, classId);
        if (db.update("UPDATE class_assignments SET status='CANCELLED' WHERE id=? AND class_id=?",
                assignmentId, classId) == 0) throw ApiException.notFound("Assignment not found");
    }

    public Map<String,Object> requireOwner(Authentication auth, long id) {
        User teacher = currentUser.require(auth, UserRole.TEACHER);
        Map<String,Object> lesson = db.one(SELECT, id);
        if (((Number) lesson.get("teacherId")).longValue() != teacher.getId()) throw ApiException.forbidden();
        return lesson;
    }

    public Map<String,Object> requireVisible(User user, long id) {
        Map<String,Object> lesson = db.one(SELECT, id);
        if (user.getRoleCode() == UserRole.ADMIN ||
                (user.getRoleCode() == UserRole.TEACHER && ((Number) lesson.get("teacherId")).longValue() == user.getId())) return lesson;
        if (user.getRoleCode() != UserRole.STUDENT || !"PUBLISHED".equals(lesson.get("status"))) throw ApiException.forbidden();
        if ("PUBLIC".equals(lesson.get("visibility"))) return lesson;
        if (!db.exists("SELECT COUNT(*) FROM lessons l WHERE l.id=? AND " + ASSIGNED, id, user.getId()))
            throw ApiException.forbidden();
        return lesson;
    }

    private Map<String,Object> withResources(Map<String,Object> lesson) {
        long id = ((Number) lesson.get("id")).longValue();
        lesson.put("resources", db.list("SELECT id, lesson_id AS lessonId, resource_type AS resourceType, title, "
                + "external_url AS externalUrl, original_name AS originalName, mime_type AS mimeType, "
                + "size_bytes AS sizeBytes, sort_order AS sortOrder, created_at AS createdAt "
                + "FROM lesson_resources WHERE lesson_id=? ORDER BY sort_order,id", id));
        return lesson;
    }

    private Map<String,Object> assignment(long id) {
        return db.one("SELECT id, class_id AS classId, target_type AS targetType, target_id AS targetId, "
                + "assigned_by AS assignedBy, available_at AS availableAt, due_at AS dueAt, status, created_at AS createdAt "
                + "FROM class_assignments WHERE id=?", id);
    }

    private static void validateVisibility(String visibility) {
        if (!"PUBLIC".equals(visibility) && !"CLASS".equals(visibility)) throw ApiException.badRequest("Invalid visibility");
    }
    private static String trim(String value) { return value == null ? null : value.trim(); }
}
