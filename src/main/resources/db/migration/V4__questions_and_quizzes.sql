CREATE TABLE IF NOT EXISTS questions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    teacher_id BIGINT UNSIGNED NOT NULL,
    topic_id BIGINT UNSIGNED NOT NULL,
    question_type VARCHAR(30) NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    stem TEXT NOT NULL,
    explanation TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_questions_teacher_status (teacher_id, status),
    INDEX idx_questions_topic_difficulty (topic_id, difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS question_options (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    question_id BIGINT UNSIGNED NOT NULL,
    content TEXT NOT NULL,
    is_correct TINYINT(1) NOT NULL DEFAULT 0,
    sort_order INT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_question_options_order (question_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS quizzes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    teacher_id BIGINT UNSIGNED NOT NULL,
    lesson_id BIGINT UNSIGNED NULL,
    topic_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    time_limit_minutes INT UNSIGNED NULL,
    max_attempts INT UNSIGNED NOT NULL DEFAULT 1,
    result_policy VARCHAR(30) NOT NULL DEFAULT 'AFTER_SUBMIT',
    visibility VARCHAR(20) NOT NULL DEFAULT 'CLASS',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_quizzes_teacher_status (teacher_id, status),
    INDEX idx_quizzes_topic_status (topic_id, status),
    INDEX idx_quizzes_lesson (lesson_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS quiz_questions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    quiz_id BIGINT UNSIGNED NOT NULL,
    source_question_id BIGINT UNSIGNED NULL,
    question_type VARCHAR(30) NOT NULL,
    stem_snapshot TEXT NOT NULL,
    explanation_snapshot TEXT NULL,
    points DECIMAL(8,2) NOT NULL DEFAULT 1.00,
    sort_order INT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_quiz_questions_order (quiz_id, sort_order),
    INDEX idx_quiz_questions_source (source_question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS quiz_question_options (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    quiz_question_id BIGINT UNSIGNED NOT NULL,
    content_snapshot TEXT NOT NULL,
    is_correct TINYINT(1) NOT NULL DEFAULT 0,
    sort_order INT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_quiz_question_options_order (quiz_question_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
