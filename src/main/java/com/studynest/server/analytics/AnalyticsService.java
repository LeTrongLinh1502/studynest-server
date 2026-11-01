package com.studynest.server.analytics;

import com.studynest.server.classroom.ClassroomService;
import com.studynest.server.common.DbRows;
import com.studynest.server.quiz.QuizService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {
    private final DbRows db;
    private final QuizService quizzes;
    private final ClassroomService classes;

    public AnalyticsService(DbRows db, QuizService quizzes, ClassroomService classes) {
        this.db = db; this.quizzes = quizzes; this.classes = classes;
    }

    public Map<String,Object> quiz(Authentication auth, long quizId) {
        Map<String,Object> quiz = quizzes.owner(auth, quizId);
        List<Map<String,Object>> attempts = db.list("SELECT a.id, a.student_id AS studentId, u.full_name AS studentName, "
                + "a.attempt_no AS attemptNo, a.score, a.max_score AS maxScore, a.submitted_at AS submittedAt, "
                + "c.name AS className FROM quiz_attempts a JOIN users u ON u.id=a.student_id "
                + "LEFT JOIN class_assignments ca ON ca.id=a.assignment_id "
                + "LEFT JOIN classes c ON c.id=ca.class_id "
                + "WHERE a.quiz_id=? AND a.status='SUBMITTED' ORDER BY a.submitted_at DESC", quizId);
        List<Map<String,Object>> questions = db.list("SELECT qq.id, qq.stem_snapshot AS stem, qq.points, "
                + "COUNT(a.id) AS attemptCount, "
                + "SUM(CASE WHEN aa.is_correct=1 THEN 1 ELSE 0 END) AS correctCount, "
                + "SUM(CASE WHEN a.id IS NOT NULL AND (aa.is_correct=0 OR aa.id IS NULL) THEN 1 ELSE 0 END) AS incorrectCount "
                + "FROM quiz_questions qq LEFT JOIN quiz_attempts a ON a.quiz_id=qq.quiz_id AND a.status='SUBMITTED' "
                + "LEFT JOIN attempt_answers aa ON aa.attempt_id=a.id AND aa.quiz_question_id=qq.id "
                + "WHERE qq.quiz_id=? GROUP BY qq.id, qq.stem_snapshot, qq.points, qq.sort_order ORDER BY qq.sort_order", quizId);
        Map<String,Object> summary = db.one("SELECT COUNT(*) AS submittedCount, COUNT(DISTINCT student_id) AS studentCount, "
                + "ROUND(AVG(CASE WHEN max_score>0 THEN score/max_score*100 END),2) AS averagePercent "
                + "FROM quiz_attempts WHERE quiz_id=? AND status='SUBMITTED'", quizId);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("quiz", quiz); result.put("summary", summary);
        result.put("attempts", attempts); result.put("questions", questions);
        return result;
    }

    public Map<String,Object> classroom(Authentication auth, long classId) {
        Map<String,Object> classroom = classes.requireOwner(auth, classId);
        Map<String,Object> summary = db.one("SELECT "
                + "(SELECT COUNT(*) FROM class_members WHERE class_id=? AND status='ACTIVE') AS studentCount, "
                + "(SELECT COUNT(*) FROM class_assignments WHERE class_id=? AND status='ACTIVE' AND target_type='QUIZ') AS quizCount, "
                + "(SELECT COUNT(*) FROM class_assignments WHERE class_id=? AND status='ACTIVE' AND target_type='LESSON') AS lessonCount",
                classId, classId, classId);
        List<Map<String,Object>> quizzes = db.list("SELECT q.id AS quizId, q.title, COUNT(a.id) AS submittedCount, "
                + "COUNT(DISTINCT a.student_id) AS studentCount, "
                + "ROUND(AVG(CASE WHEN a.max_score>0 THEN a.score/a.max_score*100 END),2) AS averagePercent "
                + "FROM class_assignments ca JOIN quizzes q ON q.id=ca.target_id AND ca.target_type='QUIZ' "
                + "LEFT JOIN quiz_attempts a ON a.assignment_id=ca.id AND a.status='SUBMITTED' "
                + "WHERE ca.class_id=? AND ca.status='ACTIVE' GROUP BY q.id,q.title ORDER BY q.title", classId);
        List<Map<String,Object>> students = db.list("SELECT m.student_id AS studentId, u.full_name AS studentName, "
                + "COUNT(DISTINCT a.id) AS submittedCount, "
                + "ROUND(AVG(CASE WHEN a.max_score>0 THEN a.score/a.max_score*100 END),2) AS averagePercent, "
                + "COUNT(DISTINCT lc.lesson_id) AS completedLessons "
                + "FROM class_members m JOIN users u ON u.id=m.student_id "
                + "LEFT JOIN class_assignments ca ON ca.class_id=m.class_id AND ca.target_type='QUIZ' AND ca.status='ACTIVE' "
                + "LEFT JOIN quiz_attempts a ON a.assignment_id=ca.id AND a.student_id=m.student_id AND a.status='SUBMITTED' "
                + "LEFT JOIN lesson_completions lc ON lc.student_id=m.student_id AND lc.lesson_id IN "
                + "(SELECT target_id FROM class_assignments WHERE class_id=m.class_id AND target_type='LESSON' AND status='ACTIVE') "
                + "WHERE m.class_id=? AND m.status='ACTIVE' GROUP BY m.student_id,u.full_name ORDER BY u.full_name", classId);
        List<Map<String,Object>> topics = db.list("SELECT t.id AS topicId, t.name AS topicName, "
                + "COUNT(a.id) AS submittedCount, "
                + "ROUND(AVG(CASE WHEN a.max_score>0 THEN a.score/a.max_score*100 END),2) AS averagePercent "
                + "FROM class_assignments ca JOIN quizzes q ON q.id=ca.target_id AND ca.target_type='QUIZ' "
                + "JOIN topics t ON t.id=q.topic_id "
                + "LEFT JOIN quiz_attempts a ON a.assignment_id=ca.id AND a.status='SUBMITTED' "
                + "WHERE ca.class_id=? AND ca.status='ACTIVE' GROUP BY t.id,t.name ORDER BY t.name", classId);
        List<Map<String,Object>> distribution = db.list("SELECT LEAST(4,FLOOR(a.score/a.max_score*5)) AS bucket, "
                + "COUNT(*) AS total FROM quiz_attempts a JOIN class_assignments ca ON ca.id=a.assignment_id "
                + "WHERE ca.class_id=? AND ca.status='ACTIVE' AND a.status='SUBMITTED' AND a.max_score>0 "
                + "GROUP BY bucket ORDER BY bucket", classId);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("classroom", classroom); result.put("summary", summary);
        result.put("quizzes", quizzes); result.put("students", students); result.put("topics", topics);
        result.put("distribution", distribution);
        return result;
    }
}
