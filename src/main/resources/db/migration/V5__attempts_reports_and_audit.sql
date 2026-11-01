CREATE TABLE IF NOT EXISTS quiz_attempts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    quiz_id BIGINT UNSIGNED NOT NULL,
    student_id BIGINT UNSIGNED NOT NULL,
    assignment_id BIGINT UNSIGNED NULL,
    attempt_no INT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    score DECIMAL(8,2) NULL,
    max_score DECIMAL(8,2) NOT NULL,
    started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    submitted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_quiz_attempts_number (quiz_id, student_id, attempt_no),
    INDEX idx_quiz_attempts_student (student_id, submitted_at),
    INDEX idx_quiz_attempts_assignment (assignment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS attempt_answers (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    attempt_id BIGINT UNSIGNED NOT NULL,
    quiz_question_id BIGINT UNSIGNED NOT NULL,
    is_correct TINYINT(1) NULL,
    points_earned DECIMAL(8,2) NULL,
    answered_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_attempt_answers_question (attempt_id, quiz_question_id),
    INDEX idx_attempt_answers_question (quiz_question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS attempt_answer_options (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    attempt_answer_id BIGINT UNSIGNED NOT NULL,
    quiz_question_option_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_attempt_answer_options_choice
        (attempt_answer_id, quiz_question_option_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS content_reports (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reporter_id BIGINT UNSIGNED NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT UNSIGNED NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by BIGINT UNSIGNED NULL,
    resolution_note TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reviewed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_content_reports_status (status, created_at),
    INDEX idx_content_reports_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_id BIGINT UNSIGNED NOT NULL,
    action_code VARCHAR(80) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id BIGINT UNSIGNED NULL,
    details TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_audit_events_actor_time (actor_id, created_at),
    INDEX idx_audit_events_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
