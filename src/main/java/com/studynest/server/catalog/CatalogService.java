package com.studynest.server.catalog;

import com.studynest.server.common.DbRows;
import com.studynest.server.common.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CatalogService {
    private static final String SUBJECT = "SELECT id, code, name, status, created_at AS createdAt FROM subjects WHERE id=?";
    private static final String TOPIC = "SELECT id, subject_id AS subjectId, parent_topic_id AS parentTopicId, code, name, sort_order AS sortOrder, status FROM topics WHERE id=?";
    private final DbRows db;

    public CatalogService(DbRows db) { this.db = db; }

    public List<Map<String, Object>> subjects(boolean all) {
        return db.list("SELECT id, code, name, status, created_at AS createdAt FROM subjects "
                + (all ? "" : "WHERE status='ACTIVE' ") + "ORDER BY name");
    }

    public List<Map<String, Object>> topics(long subjectId, boolean all) {
        Map<String, Object> subject = db.one(SUBJECT, subjectId);
        if (!all && !"ACTIVE".equals(subject.get("status"))) throw ApiException.notFound("Subject not found");
        return db.list("SELECT id, subject_id AS subjectId, parent_topic_id AS parentTopicId, code, name, sort_order AS sortOrder, status "
                + "FROM topics WHERE subject_id=? " + (all ? "" : "AND status='ACTIVE' ") + "ORDER BY sort_order, id", subjectId);
    }

    public Map<String, Object> topic(long id) { return db.one(TOPIC, id); }

    @Transactional
    public Map<String, Object> createSubject(String code, String name) {
        long id = db.insert("INSERT INTO subjects (code,name,status) VALUES (?,?,'ACTIVE')", cleanCode(code), clean(name));
        return db.one(SUBJECT, id);
    }

    @Transactional
    public Map<String, Object> updateSubject(long id, String code, String name) {
        db.one(SUBJECT, id);
        db.update("UPDATE subjects SET code=?,name=? WHERE id=?", cleanCode(code), clean(name), id);
        return db.one(SUBJECT, id);
    }

    @Transactional
    public void archiveSubject(long id) {
        db.one(SUBJECT, id);
        db.update("UPDATE subjects SET status='ARCHIVED' WHERE id=?", id);
        db.update("UPDATE topics SET status='ARCHIVED' WHERE subject_id=?", id);
    }

    @Transactional
    public Map<String, Object> createTopic(long subjectId, Long parentId, String code, String name, int sortOrder) {
        requireActiveSubject(subjectId);
        validateParent(subjectId, parentId, null);
        if (sortOrder < 0) throw ApiException.badRequest("sortOrder must be nonnegative");
        long id = db.insert("INSERT INTO topics (subject_id,parent_topic_id,code,name,sort_order,status) VALUES (?,?,?,?,?,'ACTIVE')",
                subjectId, parentId, cleanCode(code), clean(name), sortOrder);
        return topic(id);
    }

    @Transactional
    public Map<String, Object> updateTopic(long id, long subjectId, Long parentId, String code, String name, int sortOrder) {
        Map<String,Object> existing = db.one(TOPIC, id);
        if (((Number) existing.get("subjectId")).longValue() != subjectId)
            throw ApiException.badRequest("A topic cannot move to another subject");
        requireActiveSubject(subjectId);
        validateParent(subjectId, parentId, id);
        if (sortOrder < 0) throw ApiException.badRequest("sortOrder must be nonnegative");
        db.update("UPDATE topics SET subject_id=?,parent_topic_id=?,code=?,name=?,sort_order=? WHERE id=?",
                subjectId, parentId, cleanCode(code), clean(name), sortOrder, id);
        return topic(id);
    }

    @Transactional
    public void archiveTopic(long id) {
        db.one(TOPIC, id);
        ArrayDeque<Long> pending = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        pending.add(id);
        while (!pending.isEmpty()) {
            long current = pending.removeFirst();
            if (!visited.add(current)) continue;
            for (Map<String,Object> child : db.list("SELECT id FROM topics WHERE parent_topic_id=?", current))
                pending.add(((Number) child.get("id")).longValue());
        }
        for (Long topicId : visited) db.update("UPDATE topics SET status='ARCHIVED' WHERE id=?", topicId);
    }

    public void requireActiveTopic(long id) {
        Map<String, Object> topic = db.one(TOPIC, id);
        if (!"ACTIVE".equals(topic.get("status"))) throw ApiException.badRequest("Topic is archived");
        requireActiveSubject(((Number) topic.get("subjectId")).longValue());
    }

    private void requireActiveSubject(long id) {
        if (!"ACTIVE".equals(db.one(SUBJECT, id).get("status"))) throw ApiException.badRequest("Subject is archived");
    }

    private void validateParent(long subjectId, Long parentId, Long editedId) {
        Set<Long> seen = new HashSet<>();
        while (parentId != null) {
            if (!seen.add(parentId) || parentId.equals(editedId)) throw ApiException.badRequest("Topic hierarchy contains a cycle");
            Map<String, Object> parent = db.one(TOPIC, parentId);
            if (((Number) parent.get("subjectId")).longValue() != subjectId || !"ACTIVE".equals(parent.get("status")))
                throw ApiException.badRequest("Parent topic must be active and in the same subject");
            Object next = parent.get("parentTopicId");
            parentId = next == null ? null : ((Number) next).longValue();
        }
    }

    private static String clean(String value) { return value.trim(); }
    private static String cleanCode(String value) { return value.trim().toUpperCase(java.util.Locale.ROOT); }
}
