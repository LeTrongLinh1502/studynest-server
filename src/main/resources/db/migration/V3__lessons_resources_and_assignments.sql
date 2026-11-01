CREATE TABLE IF NOT EXISTS lessons (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    teacher_id BIGINT UNSIGNED NOT NULL,
    topic_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary TEXT NULL,
    body LONGTEXT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'CLASS',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_lessons_teacher_status (teacher_id, status),
    INDEX idx_lessons_topic_status (topic_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lesson_resources (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    lesson_id BIGINT UNSIGNED NOT NULL,
    resource_type VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    external_url VARCHAR(2048) NULL,
    storage_key VARCHAR(512) NULL,
    original_name VARCHAR(255) NULL,
    mime_type VARCHAR(100) NULL,
    size_bytes BIGINT UNSIGNED NULL,
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_lesson_resources_lesson_order (lesson_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS lesson_completions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    lesson_id BIGINT UNSIGNED NOT NULL,
    student_id BIGINT UNSIGNED NOT NULL,
    completed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_lesson_completions_lesson_student (lesson_id, student_id),
    INDEX idx_lesson_completions_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS class_assignments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    class_id BIGINT UNSIGNED NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT UNSIGNED NOT NULL,
    assigned_by BIGINT UNSIGNED NOT NULL,
    available_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    due_at TIMESTAMP(6) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_class_assignments_target (class_id, target_type, target_id),
    INDEX idx_class_assignments_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
