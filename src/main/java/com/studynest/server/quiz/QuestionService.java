package com.studynest.server.quiz;

import com.studynest.server.catalog.CatalogService;
import com.studynest.server.common.AuditService;
import com.studynest.server.common.CurrentUser;
import com.studynest.server.common.DbRows;
import com.studynest.server.common.exception.ApiException;
import com.studynest.server.user.entity.User;
import com.studynest.server.user.entity.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Service
public class QuestionService {
    private static final String FIELDS = "id, teacher_id AS teacherId, topic_id AS topicId, "
            + "(SELECT subject_id FROM topics WHERE topics.id=questions.topic_id) AS subjectId, "
            + "(SELECT name FROM topics WHERE topics.id=questions.topic_id) AS topicName, question_type AS questionType, "
            + "difficulty, stem, explanation, status, created_at AS createdAt, updated_at AS updatedAt";
    private final DbRows db;
    private final CurrentUser currentUser;
    private final CatalogService catalog;
    private final AuditService audit;

    public QuestionService(DbRows db, CurrentUser currentUser, CatalogService catalog, AuditService audit) {
        this.db = db; this.currentUser = currentUser; this.catalog = catalog; this.audit = audit;
    }

    public List<Map<String,Object>> mine(Authentication auth, Long topicId) {
        User teacher = currentUser.require(auth, UserRole.TEACHER);
        String sql = "SELECT " + FIELDS + " FROM questions WHERE teacher_id=? AND status<>'ARCHIVED' "
                + (topicId == null ? "" : "AND topic_id=? ") + "ORDER BY updated_at DESC";
        return topicId == null ? db.list(sql, teacher.getId()) : db.list(sql, teacher.getId(), topicId);
    }

    public Map<String,Object> one(Authentication auth, long id) {
        User teacher = currentUser.require(auth, UserRole.TEACHER);
        Map<String,Object> question = raw(id);
        if (number(question, "teacherId") != teacher.getId()) throw ApiException.forbidden();
        return withOptions(question);
    }

    @Transactional
    public Map<String,Object> create(Authentication auth, QuestionController.QuestionRequest request) {
        User teacher = currentUser.require(auth, UserRole.TEACHER);
        validate(request);
        long id = insert(teacher.getId(), request.topicId(), request.difficulty(), request.stem(),
                request.explanation(), request.options());
        return one(auth, id);
    }

    @Transactional
    public List<Map<String,Object>> createBatch(Authentication auth, QuestionController.BatchQuestionRequest request) {
        User teacher = currentUser.require(auth, UserRole.TEACHER);
        catalog.requireActiveTopic(request.topicId());
        for (int i = 0; i < request.questions().size(); i++) {
            QuestionController.BatchQuestionItem item = request.questions().get(i);
            validateOptions(item.options(), i + 1);
        }
        List<Map<String,Object>> created = new ArrayList<>();
        for (QuestionController.BatchQuestionItem item : request.questions()) {
            long id = insert(teacher.getId(), request.topicId(), item.difficulty(), item.stem(),
                    item.explanation(), item.options());
            created.add(one(auth, id));
        }
        return created;
    }

    @Transactional
    public Map<String,Object> update(Authentication auth, long id, QuestionController.QuestionRequest request) {
        Map<String,Object> question = one(auth, id);
        if ("ARCHIVED".equals(question.get("status"))) throw ApiException.conflict("Question is archived");
        validate(request);
        db.update("UPDATE questions SET topic_id=?,difficulty=?,stem=?,explanation=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",
                request.topicId(), request.difficulty().name(), request.stem().trim(), trim(request.explanation()), id);
        db.update("DELETE FROM question_options WHERE question_id=?", id);
        writeOptions(id, request.options());
        return one(auth, id);
    }

    @Transactional
    public void archive(Authentication auth, long id) {
        User teacher = currentUser.require(auth, UserRole.TEACHER);
        one(auth, id);
        db.update("UPDATE questions SET status='ARCHIVED',updated_at=CURRENT_TIMESTAMP(6) WHERE id=?", id);
        audit.record(teacher.getId(), "QUESTION_ARCHIVED", "QUESTION", id, null);
    }

    public Map<String,Object> reusable(long id, long teacherId) {
        Map<String,Object> question = raw(id);
        if (number(question, "teacherId") != teacherId || "ARCHIVED".equals(question.get("status")))
            throw ApiException.badRequest("Question must belong to the teacher and be active");
        return withOptions(question);
    }

    private void validate(QuestionController.QuestionRequest request) {
        catalog.requireActiveTopic(request.topicId());
        validateOptions(request.options(), null);
    }

    private void validateOptions(List<QuestionController.OptionRequest> options, Integer questionNumber) {
        String prefix = questionNumber == null ? "" : "Question " + questionNumber + ": ";
        if (options == null || options.size() < 2 || options.size() > 8)
            throw ApiException.badRequest(prefix + "a question needs 2 to 8 choices");
        if (options.stream().filter(QuestionController.OptionRequest::isCorrect).count() != 1)
            throw ApiException.badRequest(prefix + "choose exactly one correct option");
    }

    private long insert(long teacherId, long topicId, QuestionController.Difficulty difficulty,
                        String stem, String explanation, List<QuestionController.OptionRequest> options) {
        long id = db.insert("INSERT INTO questions (teacher_id,topic_id,question_type,difficulty,stem,explanation,status) "
                + "VALUES (?,?,'SINGLE_CHOICE',?,?,?,'DRAFT')", teacherId, topicId,
                difficulty.name(), stem.trim(), trim(explanation));
        writeOptions(id, options);
        return id;
    }

    private void writeOptions(long questionId, List<QuestionController.OptionRequest> options) {
        for (int i = 0; i < options.size(); i++) {
            QuestionController.OptionRequest option = options.get(i);
            db.insert("INSERT INTO question_options (question_id,content,is_correct,sort_order) VALUES (?,?,?,?)",
                    questionId, option.content().trim(), option.isCorrect(), i);
        }
    }

    private Map<String,Object> raw(long id) { return db.one("SELECT " + FIELDS + " FROM questions WHERE id=?", id); }
    private Map<String,Object> withOptions(Map<String,Object> question) {
        question.put("options", db.list("SELECT id, question_id AS questionId, content, is_correct AS isCorrect, sort_order AS sortOrder "
                + "FROM question_options WHERE question_id=? ORDER BY sort_order", question.get("id")));
        return question;
    }
    private static long number(Map<String,Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private static String trim(String value) { return value == null ? null : value.trim(); }
}
