package com.studynest.server.attempt;

import com.studynest.server.common.DbRows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AttemptExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(AttemptExpiryJob.class);
    private final DbRows db;
    private final AttemptService attempts;

    public AttemptExpiryJob(DbRows db, AttemptService attempts) { this.db = db; this.attempts = attempts; }

    @Scheduled(fixedDelay=60000)
    public void submitExpired() {
        for (Map<String,Object> row : db.list("SELECT a.id FROM quiz_attempts a JOIN quizzes q ON q.id=a.quiz_id "
                + "LEFT JOIN class_assignments ca ON ca.id=a.assignment_id WHERE a.status='IN_PROGRESS' "
                + "AND ((q.time_limit_minutes IS NOT NULL AND TIMESTAMPADD(MINUTE,q.time_limit_minutes,a.started_at)<=CURRENT_TIMESTAMP(6)) "
                + "OR (ca.due_at IS NOT NULL AND ca.due_at<=CURRENT_TIMESTAMP(6))) ORDER BY a.started_at LIMIT 500")) {
            long id = ((Number) row.get("id")).longValue();
            try { attempts.expireIfNeeded(id); }
            catch (RuntimeException exception) { log.warn("Could not finish expired attempt {}", id, exception); }
        }
    }
}
