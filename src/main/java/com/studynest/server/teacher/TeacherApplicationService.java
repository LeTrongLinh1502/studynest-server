package com.studynest.server.teacher;

import com.studynest.server.common.AuditService;
import com.studynest.server.common.CurrentUser;
import com.studynest.server.common.DbRows;
import com.studynest.server.common.exception.ApiException;
import com.studynest.server.user.entity.User;
import com.studynest.server.user.entity.UserRole;
import com.studynest.server.user.entity.UserStatus;
import com.studynest.server.user.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class TeacherApplicationService {
    private static final String SELECT = "SELECT a.id, a.user_id AS userId, a.reason, a.status, "
            + "a.reviewed_by AS reviewedBy, a.review_note AS reviewNote, a.created_at AS createdAt, a.reviewed_at AS reviewedAt "
            + "FROM teacher_applications a WHERE a.id=?";
    private final DbRows db;
    private final CurrentUser currentUser;
    private final UserRepository users;
    private final AuditService audit;

    public TeacherApplicationService(DbRows db, CurrentUser currentUser, UserRepository users, AuditService audit) {
        this.db = db; this.currentUser = currentUser; this.users = users; this.audit = audit;
    }

    @Transactional
    public Map<String,Object> apply(Authentication auth, String reason) {
        User applicant = currentUser.require(auth, UserRole.STUDENT);
        users.lockById(applicant.getId()).orElseThrow(() -> ApiException.notFound("User not found"));
        if (db.exists("SELECT COUNT(*) FROM teacher_applications WHERE user_id=? AND status='PENDING'", applicant.getId()))
            throw ApiException.conflict("A pending application already exists");
        long id = db.insert("INSERT INTO teacher_applications (user_id,reason,status) VALUES (?,?,'PENDING')",
                applicant.getId(), reason == null ? null : reason.trim());
        return db.one(SELECT, id);
    }

    public List<Map<String,Object>> mine(Authentication auth) {
        User applicant = currentUser.get(auth);
        return db.list("SELECT id, user_id AS userId, reason, status, reviewed_by AS reviewedBy, "
                + "review_note AS reviewNote, created_at AS createdAt, reviewed_at AS reviewedAt "
                + "FROM teacher_applications WHERE user_id=? ORDER BY created_at DESC", applicant.getId());
    }

    public List<Map<String,Object>> all(String status) {
        if (status != null && !List.of("PENDING", "APPROVED", "REJECTED").contains(status))
            throw ApiException.badRequest("Invalid status");
        String sql = "SELECT a.id, a.user_id AS userId, u.full_name AS fullName, u.email, a.reason, a.status, "
                + "a.reviewed_by AS reviewedBy, a.review_note AS reviewNote, a.created_at AS createdAt, a.reviewed_at AS reviewedAt "
                + "FROM teacher_applications a JOIN users u ON u.id=a.user_id "
                + (status == null ? "" : "WHERE a.status=? ") + "ORDER BY a.created_at DESC";
        return status == null ? db.list(sql) : db.list(sql, status);
    }

    @Transactional
    public Map<String,Object> review(Authentication auth, long id, String decision, String note) {
        User admin = currentUser.require(auth, UserRole.ADMIN);
        Map<String,Object> application = db.one(SELECT, id);
        long applicantId = ((Number) application.get("userId")).longValue();
        User applicant = users.lockById(applicantId).orElseThrow(() -> ApiException.notFound("Applicant not found"));
        application = db.one(SELECT + " FOR UPDATE", id);
        if (!"PENDING".equals(application.get("status"))) throw ApiException.conflict("Application has already been reviewed");
        if ("APPROVED".equals(decision)) {
            if (applicant.getRoleCode() != UserRole.STUDENT || applicant.getStatus() != UserStatus.ACTIVE)
                throw ApiException.conflict("Applicant is no longer an active student");
            applicant.promoteToTeacher();
        } else if (!"REJECTED".equals(decision)) {
            throw ApiException.badRequest("Decision must be APPROVED or REJECTED");
        }
        db.update("UPDATE teacher_applications SET status=?,reviewed_by=?,review_note=?,reviewed_at=CURRENT_TIMESTAMP(6) WHERE id=?",
                decision, admin.getId(), note == null ? null : note.trim(), id);
        audit.record(admin.getId(), "TEACHER_APPLICATION_" + decision, "TEACHER_APPLICATION", id, note);
        return db.one(SELECT, id);
    }
}
