package com.studynest.server.classroom;

import com.studynest.server.common.CurrentUser;
import com.studynest.server.common.DbRows;
import com.studynest.server.common.exception.ApiException;
import com.studynest.server.user.entity.User;
import com.studynest.server.user.entity.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ClassroomService {
    private static final String SELECT = "SELECT id, teacher_id AS teacherId, name, description, join_code AS joinCode, status, created_at AS createdAt FROM classes WHERE id=?";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    private final DbRows db;
    private final CurrentUser currentUser;

    public ClassroomService(DbRows db, CurrentUser currentUser) { this.db = db; this.currentUser = currentUser; }

    @Transactional
    public Map<String,Object> create(Authentication auth, String name, String description) {
        User teacher = currentUser.require(auth, UserRole.TEACHER);
        String code = newCode();
        long id = db.insert("INSERT INTO classes (teacher_id,name,description,join_code,status) VALUES (?,?,?,?,'ACTIVE')",
                teacher.getId(), name.trim(), description == null ? null : description.trim(), code);
        return db.one(SELECT, id);
    }

    public List<Map<String,Object>> mineTeacher(Authentication auth) {
        User teacher = currentUser.require(auth, UserRole.TEACHER);
        return db.list("SELECT c.id, c.teacher_id AS teacherId, c.name, c.description, c.join_code AS joinCode, c.status, "
                + "c.created_at AS createdAt, "
                + "(SELECT COUNT(*) FROM class_members m WHERE m.class_id=c.id AND m.status='ACTIVE') AS memberCount, "
                + "(SELECT COUNT(*) FROM class_assignments a WHERE a.class_id=c.id AND a.status='ACTIVE' AND a.target_type='LESSON') AS lessonCount, "
                + "(SELECT COUNT(*) FROM class_assignments a WHERE a.class_id=c.id AND a.status='ACTIVE' AND a.target_type='QUIZ') AS quizCount "
                + "FROM classes c WHERE c.teacher_id=? ORDER BY c.created_at DESC", teacher.getId());
    }

    public Map<String,Object> teacherClass(Authentication auth, long id) { requireOwner(auth, id); return db.one(SELECT, id); }

    @Transactional
    public Map<String,Object> update(Authentication auth, long id, String name, String description) {
        if (!"ACTIVE".equals(requireOwner(auth, id).get("status"))) throw ApiException.conflict("Class is archived");
        db.update("UPDATE classes SET name=?,description=? WHERE id=?", name.trim(), description == null ? null : description.trim(), id);
        return db.one(SELECT, id);
    }

    @Transactional
    public void archive(Authentication auth, long id) {
        requireOwner(auth, id);
        db.update("UPDATE classes SET status='ARCHIVED' WHERE id=?", id);
        db.update("UPDATE class_assignments SET status='CANCELLED' WHERE class_id=?", id);
    }

    public List<Map<String,Object>> members(Authentication auth, long id) {
        requireOwnerOrAdmin(auth, id);
        return db.list("SELECT m.id, m.class_id AS classId, m.student_id AS studentId, u.full_name AS fullName, u.email, "
                + "m.status, m.joined_at AS joinedAt FROM class_members m JOIN users u ON u.id=m.student_id "
                + "WHERE m.class_id=? ORDER BY m.joined_at DESC", id);
    }

    @Transactional
    public void removeMember(Authentication auth, long classId, long studentId) {
        requireOwner(auth, classId);
        if (db.update("UPDATE class_members SET status='REMOVED' WHERE class_id=? AND student_id=? AND status='ACTIVE'",
                classId, studentId) == 0) throw ApiException.notFound("Active member not found");
    }

    @Transactional
    public Map<String,Object> join(Authentication auth, String joinCode) {
        User student = currentUser.require(auth, UserRole.STUDENT);
        String code = joinCode.trim().toUpperCase(Locale.ROOT);
        Map<String,Object> classroom = db.one(SELECT.replace("WHERE id=?", "WHERE join_code=? FOR UPDATE"), code);
        if (!"ACTIVE".equals(classroom.get("status"))) throw ApiException.conflict("Class is archived");
        long classId = ((Number) classroom.get("id")).longValue();
        Map<String,Object> existing = db.optional("SELECT id, status FROM class_members WHERE class_id=? AND student_id=?", classId, student.getId());
        if (existing == null) {
            db.insert("INSERT INTO class_members (class_id,student_id,status) VALUES (?,?,'ACTIVE')", classId, student.getId());
        } else if (!"ACTIVE".equals(existing.get("status"))) {
            throw ApiException.conflict("Membership is not active; contact the teacher");
        }
        return studentClass(classId);
    }

    public List<Map<String,Object>> mineStudent(Authentication auth) {
        User student = currentUser.require(auth, UserRole.STUDENT);
        return db.list("SELECT c.id, c.teacher_id AS teacherId, u.full_name AS teacherName, c.name, c.description, c.status, "
                + "c.created_at AS createdAt, m.joined_at AS joinedAt, "
                + "(SELECT COUNT(*) FROM class_members cm WHERE cm.class_id=c.id AND cm.status='ACTIVE') AS memberCount, "
                + "(SELECT COUNT(*) FROM class_assignments a WHERE a.class_id=c.id AND a.status='ACTIVE' AND a.target_type='LESSON') AS lessonCount, "
                + "(SELECT COUNT(*) FROM class_assignments a WHERE a.class_id=c.id AND a.status='ACTIVE' AND a.target_type='QUIZ') AS quizCount, "
                + "(SELECT COUNT(*) FROM class_assignments a JOIN lesson_completions lc ON lc.lesson_id=a.target_id "
                + "AND lc.student_id=? WHERE a.class_id=c.id AND a.status='ACTIVE' AND a.target_type='LESSON') AS completedLessons, "
                + "(SELECT COUNT(DISTINCT a.id) FROM class_assignments a JOIN quiz_attempts qa ON qa.assignment_id=a.id "
                + "AND qa.student_id=? AND qa.status='SUBMITTED' WHERE a.class_id=c.id AND a.status='ACTIVE' AND a.target_type='QUIZ') AS submittedQuizzes "
                + "FROM classes c "
                + "JOIN class_members m ON m.class_id=c.id JOIN users u ON u.id=c.teacher_id "
                + "WHERE m.student_id=? AND m.status='ACTIVE' AND c.status='ACTIVE' ORDER BY m.joined_at DESC",
                student.getId(), student.getId(), student.getId());
    }

    public Map<String,Object> studentClass(long id) {
        return db.one("SELECT c.id, c.teacher_id AS teacherId, u.full_name AS teacherName, c.name, c.description, c.status, "
                + "c.created_at AS createdAt FROM classes c JOIN users u ON u.id=c.teacher_id WHERE c.id=?", id);
    }

    public Map<String,Object> requireOwner(Authentication auth, long id) {
        User teacher = currentUser.require(auth, UserRole.TEACHER);
        Map<String,Object> classroom = db.one(SELECT, id);
        if (((Number) classroom.get("teacherId")).longValue() != teacher.getId()) throw ApiException.forbidden();
        return classroom;
    }

    public void requireOwnerOrAdmin(Authentication auth, long id) {
        User user = currentUser.get(auth);
        Map<String,Object> classroom = db.one(SELECT, id);
        if (user.getRoleCode() != UserRole.ADMIN &&
                (user.getRoleCode() != UserRole.TEACHER || ((Number) classroom.get("teacherId")).longValue() != user.getId()))
            throw ApiException.forbidden();
    }

    private String newCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder(8);
            for (int i = 0; i < 8; i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            if (!db.exists("SELECT COUNT(*) FROM classes WHERE join_code=?", code.toString())) return code.toString();
        }
        throw ApiException.conflict("Unable to generate a class code");
    }
}
