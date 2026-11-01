CREATE TABLE IF NOT EXISTS attempt_question_flags (
    attempt_id BIGINT UNSIGNED NOT NULL,
    quiz_question_id BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (attempt_id, quiz_question_id),
    INDEX idx_attempt_question_flags_question (quiz_question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
