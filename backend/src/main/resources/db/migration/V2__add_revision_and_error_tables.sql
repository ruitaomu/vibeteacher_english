CREATE TABLE IF NOT EXISTS revision_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    level VARCHAR(64) NULL,
    model VARCHAR(128) NULL,
    requirements LONGTEXT NULL,
    expand BOOLEAN NOT NULL DEFAULT FALSE,
    source_text LONGTEXT NOT NULL,
    corrected_text LONGTEXT NULL,
    summary LONGTEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;

CREATE INDEX idx_revision_record_user_created ON revision_record(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS error_occurrence (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    revision_id BIGINT NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    error_type VARCHAR(128) NULL,
    original_text LONGTEXT NULL,
    suggested_text LONGTEXT NULL,
    reason LONGTEXT NULL,
    CONSTRAINT fk_error_revision FOREIGN KEY (revision_id) REFERENCES revision_record(id) ON DELETE CASCADE
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;

CREATE INDEX idx_error_occurrence_user ON error_occurrence(user_id);
CREATE INDEX idx_error_occurrence_type ON error_occurrence(error_type);
