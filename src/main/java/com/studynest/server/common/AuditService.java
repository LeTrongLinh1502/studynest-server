package com.studynest.server.common;

import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final DbRows db;
    public AuditService(DbRows db) { this.db = db; }

    public void record(long actorId, String action, String targetType, Long targetId, String details) {
        db.insert("INSERT INTO audit_events (actor_id,action_code,target_type,target_id,details) VALUES (?,?,?,?,?)",
                actorId, action, targetType, targetId, details);
    }
}
