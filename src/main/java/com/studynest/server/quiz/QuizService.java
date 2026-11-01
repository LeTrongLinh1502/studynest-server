package com.studynest.server.quiz;

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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class QuizService {
    private static final String FIELDS = "q.id, q.teacher_id AS teacherId, q.lesson_id AS lessonId, q.topic_id AS topicId, "
            + "(SELECT subject_id FROM topics WHERE topics.id=q.topic_id) AS subjectId, "
            + "(SELECT name FROM topics WHERE topics.id=q.topic_id) AS topicName, "
            + "(SELECT s.name FROM topics t JOIN subjects s ON s.id=t.subject_id WHERE t.id=q.topic_id) AS subjectName, "
            + "(SELECT full_name FROM users WHERE users.id=q.teacher_id) AS teacherName, "
            + "(SELECT COUNT(*) FROM quiz_questions qq WHERE qq.quiz_id=q.id) AS questionCount, "
            + "q.title, q.description, q.time_limit_minutes AS timeLimitMinutes, q.max_attempts AS maxAttempts, "
            + "q.result_policy AS resultPolicy, q.visibility, q.status, q.published_at AS publishedAt, q.created_at AS createdAt";
    private static final String QUIZ = "SELECT " + FIELDS + " FROM quizzes q WHERE q.id=?";
    private static final String ASSIGNMENT = "SELECT a.id, a.class_id AS classId, a.target_id AS quizId, "
            + "a.available_at AS availableAt, a.due_at AS dueAt, a.status FROM class_assignments a "
            + "JOIN classes c ON c.id=a.class_id JOIN class_members m ON m.class_id=c.id "
            + "WHERE a.target_type='QUIZ' AND a.target_id=? AND a.status='ACTIVE' AND c.status='ACTIVE' "
            + "AND m.student_id=? AND m.status='ACTIVE' AND a.available_at<=CURRENT_TIMESTAMP(6) "
            + "ORDER BY a.due_at IS NULL, a.due_at, a.id";
    private final DbRows db;
    private final CurrentUser currentUser;
    private final CatalogService catalog;
    private final QuestionService questions;
    private final ClassroomService classrooms;
    private final AuditService audit;

    public QuizService(DbRows db, CurrentUser currentUser, CatalogService catalog, QuestionService questions,
                       ClassroomService classrooms, AuditService audit) {
        this.db = db; this.currentUser = currentUser; this.catalog = catalog;
        this.questions = questions; this.classrooms = classrooms; this.audit = audit;
    }

    public record Access(Map<String,Object> quiz, Map<String,Object> assignment) {}

    public List<Map<String,Object>> teacherMine(Authentication auth) {
        User teacher = currentUser.require(auth, UserRole.TEACHER);
        return db.list("SELECT " + FIELDS + " FROM quizzes q WHERE q.teacher_id=? ORDER BY q.created_at DESC", teacher.getId());
    }

    public Map<String,Object> teacherOne(Authentication auth, long id) {
        Map<String,Object> quiz = owner(auth, id);
        quiz.put("questions", snapshotQuestions(id, true));
        return quiz;
    }

    @Transactional
    public Map<String,Object> create(Authentication auth, QuizController.QuizRequest request) {
        User teacher = currentUser.require(auth, UserRole.TEACHER);
        validateRequest(request, teacher.getId());
        long id = db.insert("INSERT INTO quizzes (teacher_id,lesson_id,topic_id,title,description,time_limit_minutes,max_attempts,result_policy,visibility,status) "
                + "VALUES (?,?,?,?,?,?,?,?,?,'DRAFT')", teacher.getId(), request.lessonId(), request.topicId(), request.title().trim(),
                trim(request.description()), request.timeLimitMinutes(), request.maxAttempts(), request.resultPolicy().name(), request.visibility().name());
        writeSnapshots(id, teacher.getId(), request.topicId(), request.questions());
        return teacherOne(auth, id);
    }

    @Transactional
    public Map<String,Object> update(Authentication auth, long id, QuizController.QuizRequest request) {
        User teacher = currentUser.require(auth, UserRole.TEACHER);
        Map<String,Object> quiz = owner(auth, id);
        if (!"DRAFT".equals(quiz.get("status"))) throw ApiException.conflict("Only a draft quiz can be edited");
        validateRequest(request, teacher.getId());
        db.update("UPDATE quizzes SET lesson_id=?,topic_id=?,title=?,description=?,time_limit_minutes=?,max_attempts=?,result_policy=?,visibility=? WHERE id=?",
                request.lessonId(), request.topicId(), request.title().trim(), trim(request.description()), request.timeLimitMinutes(),
                request.maxAttempts(), request.resultPolicy().name(), request.visibility().name(), id);
        clearSnapshots(id);
        writeSnapshots(id, teacher.getId(), request.topicId(), request.questions());
        return teacherOne(auth, id);
    }

    @Transactional
    public Map<String,Object> publish(Authentication auth, long id) {
        User teacher = currentUser.require(auth, UserRole.TEACHER);
        Map<String,Object> quiz = owner(auth, id);
        if (!"DRAFT".equals(quiz.get("status"))) throw ApiException.conflict("Only a draft quiz can be published");
        long topicId = number(quiz, "topicId");
        catalog.requireActiveTopic(topicId);
        List<Map<String,Object>> selected = db.list("SELECT source_question_id AS sourceQuestionId, points FROM quiz_questions "
                + "WHERE quiz_id=? ORDER BY sort_order", id);
        if (selected.isEmpty()) throw ApiException.badRequest("Quiz needs at least one question");
        clearSnapshots(id);
        for (int i = 0; i < selected.size(); i++) {
            Map<String,Object> choice = selected.get(i);
            snapshot(id, teacher.getId(), topicId, number(choice, "sourceQuestionId"),
                    (BigDecimal) choice.get("points"), i);
        }
        db.update("UPDATE quizzes SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP(6) WHERE id=?", id);
        audit.record(teacher.getId(), "QUIZ_PUBLISHED", "QUIZ", id, null);
        return teacherOne(auth, id);
    }

    @Transactional
    public void archive(Authentication auth, long id) {
        User teacher = currentUser.require(auth, UserRole.TEACHER);
        owner(auth, id);
        db.update("UPDATE quizzes SET status='ARCHIVED' WHERE id=?", id);
        db.update("UPDATE class_assignments SET status='CANCELLED' WHERE target_type='QUIZ' AND target_id=?", id);
        audit.record(teacher.getId(), "QUIZ_ARCHIVED", "QUIZ", id, null);
    }

    public List<Map<String,Object>> studentList(Authentication auth, Long topicId) {
        User student = currentUser.require(auth, UserRole.STUDENT);
        String sql = "SELECT " + FIELDS + ", (SELECT COUNT(*) FROM quiz_attempts qa WHERE qa.quiz_id=q.id "
                + "AND qa.student_id=?) AS attemptCount FROM quizzes q WHERE q.status='PUBLISHED' AND "
                + "(q.visibility='PUBLIC' OR EXISTS (SELECT 1 FROM class_assignments a "
                + "JOIN classes c ON c.id=a.class_id JOIN class_members m ON m.class_id=c.id "
                + "WHERE a.target_type='QUIZ' AND a.target_id=q.id AND a.status='ACTIVE' AND c.status='ACTIVE' "
                + "AND m.student_id=? AND m.status='ACTIVE' AND a.available_at<=CURRENT_TIMESTAMP(6))) "
                + (topicId == null ? "" : "AND q.topic_id=? ") + "ORDER BY q.published_at DESC";
        return topicId == null ? db.list(sql, student.getId(), student.getId())
                : db.list(sql, student.getId(), student.getId(), topicId);
    }

    public Map<String,Object> studentOne(Authentication auth, long id) {
        User student = currentUser.require(auth, UserRole.STUDENT);
        Access access = access(student, id, false);
        Map<String,Object> quiz = access.quiz();
        quiz.put("questions", snapshotQuestions(id, false));
        quiz.put("assignmentId", access.assignment() == null ? null : access.assignment().get("id"));
        quiz.put("dueAt", access.assignment() == null ? null : access.assignment().get("dueAt"));
        quiz.put("attemptCount", db.one("SELECT COUNT(*) AS total FROM quiz_attempts WHERE quiz_id=? AND student_id=?",
                id, student.getId()).get("total"));
        quiz.put("completedAttemptCount", db.one("SELECT COUNT(*) AS total FROM quiz_attempts WHERE quiz_id=? "
                + "AND student_id=? AND status='SUBMITTED'", id, student.getId()).get("total"));
        return quiz;
    }

    public Access access(User student, long id, boolean requireStart) {
        Map<String,Object> quiz = db.one(QUIZ, id);
        if (student.getRoleCode() != UserRole.STUDENT || !"PUBLISHED".equals(quiz.get("status"))) throw ApiException.forbidden();
        if ("PUBLIC".equals(quiz.get("visibility"))) return new Access(quiz, null);
        List<Map<String,Object>> assignments = db.list(ASSIGNMENT, id, student.getId());
        if (assignments.isEmpty()) throw ApiException.forbidden();
        Map<String,Object> selected = assignments.stream().filter(row -> row.get("dueAt") == null ||
                ((Instant) row.get("dueAt")).isAfter(Instant.now())).findFirst().orElse(null);
        if (selected == null && requireStart) throw ApiException.conflict("The quiz deadline has passed");
        return new Access(quiz, selected == null ? assignments.getFirst() : selected);
    }

    @Transactional
    public Map<String,Object> assign(Authentication auth, long classId, long quizId, Instant availableAt, Instant dueAt) {
        User teacher = currentUser.require(auth, UserRole.TEACHER);
        Map<String,Object> classroom = classrooms.requireOwner(auth, classId);
        Map<String,Object> quiz = owner(auth, quizId);
        if (!"ACTIVE".equals(classroom.get("status")) || !"PUBLISHED".equals(quiz.get("status")))
            throw ApiException.conflict("Class must be active and quiz must be published");
        Instant from = availableAt == null ? Instant.now() : availableAt;
        if (dueAt != null && !dueAt.isAfter(from)) throw ApiException.badRequest("dueAt must be after availableAt");
        if ("AFTER_DUE".equals(quiz.get("resultPolicy")) && dueAt == null)
            throw ApiException.badRequest("AFTER_DUE quizzes need a dueAt value");
        Map<String,Object> existing = db.optional("SELECT id FROM class_assignments WHERE class_id=? AND target_type='QUIZ' AND target_id=?", classId, quizId);
        long id;
        if (existing == null) {
            id = db.insert("INSERT INTO class_assignments (class_id,target_type,target_id,assigned_by,available_at,due_at,status) "
                    + "VALUES (?,'QUIZ',?,?,?,?,'ACTIVE')", classId, quizId, teacher.getId(), Timestamp.from(from),
                    dueAt == null ? null : Timestamp.from(dueAt));
        } else {
            id = number(existing, "id");
            db.update("UPDATE class_assignments SET assigned_by=?,available_at=?,due_at=?,status='ACTIVE' WHERE id=?",
                    teacher.getId(), Timestamp.from(from), dueAt == null ? null : Timestamp.from(dueAt), id);
        }
        return db.one("SELECT id, class_id AS classId, target_type AS targetType, target_id AS targetId, "
                + "available_at AS availableAt, due_at AS dueAt, status FROM class_assignments WHERE id=?", id);
    }

    public List<Map<String,Object>> studentAssignments(Authentication auth) {
        User student = currentUser.require(auth, UserRole.STUDENT);
        return db.list("SELECT a.id, a.class_id AS classId, c.name AS className, a.target_id AS quizId, q.title AS quizTitle, "
                + "q.topic_id AS topicId, t.name AS topicName, t.subject_id AS subjectId, s.name AS subjectName, "
                + "q.time_limit_minutes AS timeLimitMinutes, q.max_attempts AS maxAttempts, "
                + "(SELECT COUNT(*) FROM quiz_questions qq WHERE qq.quiz_id=q.id) AS questionCount, "
                + "(SELECT COUNT(*) FROM quiz_attempts qa WHERE qa.quiz_id=q.id AND qa.student_id=?) AS attemptCount, "
                + "a.available_at AS availableAt, a.due_at AS dueAt, a.status FROM class_assignments a "
                + "JOIN classes c ON c.id=a.class_id JOIN class_members m ON m.class_id=c.id "
                + "JOIN quizzes q ON q.id=a.target_id AND a.target_type='QUIZ' "
                + "JOIN topics t ON t.id=q.topic_id JOIN subjects s ON s.id=t.subject_id "
                + "WHERE m.student_id=? AND m.status='ACTIVE' "
                + "AND c.status='ACTIVE' AND a.status='ACTIVE' AND q.status='PUBLISHED' ORDER BY a.available_at DESC",
                student.getId(), student.getId());
    }

    public Map<String,Object> owner(Authentication auth, long id) {
        User teacher = currentUser.require(auth, UserRole.TEACHER);
        Map<String,Object> quiz = db.one(QUIZ, id);
        if (number(quiz, "teacherId") != teacher.getId()) throw ApiException.forbidden();
        return quiz;
    }

    public Map<String,Object> raw(long id) { return db.one(QUIZ, id); }

    private void validateRequest(QuizController.QuizRequest request, long teacherId) {
        catalog.requireActiveTopic(request.topicId());
        if (request.resultPolicy() == QuizController.ResultPolicy.AFTER_DUE && request.visibility() == QuizController.Visibility.PUBLIC)
            throw ApiException.badRequest("AFTER_DUE requires CLASS visibility");
        if (request.lessonId() != null) {
            Map<String,Object> lesson = db.one("SELECT teacher_id AS teacherId, topic_id AS topicId, status FROM lessons WHERE id=?", request.lessonId());
            if (number(lesson, "teacherId") != teacherId || number(lesson, "topicId") != request.topicId()
                    || "ARCHIVED".equals(lesson.get("status"))) throw ApiException.badRequest("Related lesson must belong to this teacher and topic");
        }
        Set<Long> seen = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (QuizController.SelectedQuestion selected : request.questions()) {
            if (!seen.add(selected.questionId())) throw ApiException.badRequest("Quiz contains a duplicate question");
            Map<String,Object> question = questions.reusable(selected.questionId(), teacherId);
            if (number(question, "topicId") != request.topicId()) throw ApiException.badRequest("All questions must match quiz topic");
            total = total.add(selected.points());
        }
        if (total.compareTo(new BigDecimal("999999.99")) > 0) throw ApiException.badRequest("Total points are too high");
    }

    private void writeSnapshots(long quizId, long teacherId, long topicId, List<QuizController.SelectedQuestion> selected) {
        for (int i = 0; i < selected.size(); i++) snapshot(quizId, teacherId, topicId, selected.get(i).questionId(), selected.get(i).points(), i);
    }

    @SuppressWarnings("unchecked")
    private void snapshot(long quizId, long teacherId, long topicId, long questionId, BigDecimal points, int order) {
        Map<String,Object> source = questions.reusable(questionId, teacherId);
        if (number(source, "topicId") != topicId) throw ApiException.badRequest("Question topic does not match quiz topic");
        long snapshotId = db.insert("INSERT INTO quiz_questions (quiz_id,source_question_id,question_type,stem_snapshot,explanation_snapshot,points,sort_order) "
                + "VALUES (?,?,'SINGLE_CHOICE',?,?,?,?)", quizId, questionId, source.get("stem"), source.get("explanation"), points, order);
        List<Map<String,Object>> options = (List<Map<String,Object>>) source.get("options");
        for (int i = 0; i < options.size(); i++) {
            Map<String,Object> option = options.get(i);
            db.insert("INSERT INTO quiz_question_options (quiz_question_id,content_snapshot,is_correct,sort_order) VALUES (?,?,?,?)",
                    snapshotId, option.get("content"), option.get("isCorrect"), i);
        }
    }

    private void clearSnapshots(long quizId) {
        db.update("DELETE o FROM quiz_question_options o JOIN quiz_questions q ON q.id=o.quiz_question_id WHERE q.quiz_id=?", quizId);
        db.update("DELETE FROM quiz_questions WHERE quiz_id=?", quizId);
    }

    private List<Map<String,Object>> snapshotQuestions(long quizId, boolean teacherView) {
        List<Map<String,Object>> rows = db.list("SELECT id, quiz_id AS quizId, source_question_id AS sourceQuestionId, "
                + "question_type AS questionType, stem_snapshot AS stem, "
                + (teacherView ? "explanation_snapshot AS explanation, " : "")
                + "points, sort_order AS sortOrder FROM quiz_questions WHERE quiz_id=? ORDER BY sort_order", quizId);
        for (Map<String,Object> row : rows) {
            String sql = "SELECT id, quiz_question_id AS quizQuestionId, content_snapshot AS content, "
                    + (teacherView ? "is_correct AS isCorrect, " : "")
                    + "sort_order AS sortOrder FROM quiz_question_options WHERE quiz_question_id=? ORDER BY sort_order";
            row.put("options", db.list(sql, row.get("id")));
        }
        return rows;
    }

    private static long number(Map<String,Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private static String trim(String value) { return value == null ? null : value.trim(); }
}
