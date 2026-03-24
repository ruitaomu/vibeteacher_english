CREATE TABLE IF NOT EXISTS user_progress (
    user_id VARCHAR(128) PRIMARY KEY,
    total_submissions INT NOT NULL DEFAULT 0,
    current_streak INT NOT NULL DEFAULT 0,
    longest_streak INT NOT NULL DEFAULT 0,
    suggested_vocabulary_usage_count INT NOT NULL DEFAULT 0,
    last_submission_date DATE NULL
);

CREATE TABLE IF NOT EXISTS user_vocabulary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    word VARCHAR(128) NOT NULL,
    meaning VARCHAR(1024) NULL,
    example VARCHAR(2048) NULL,
    CONSTRAINT uk_user_word UNIQUE (user_id, word),
    CONSTRAINT fk_vocab_progress_user FOREIGN KEY (user_id) REFERENCES user_progress(user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_achievement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    achievement_id VARCHAR(128) NOT NULL,
    earned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_achievement UNIQUE (user_id, achievement_id),
    CONSTRAINT fk_achievement_progress_user FOREIGN KEY (user_id) REFERENCES user_progress(user_id) ON DELETE CASCADE
);
