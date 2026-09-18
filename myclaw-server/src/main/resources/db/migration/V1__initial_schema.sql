CREATE TABLE IF NOT EXISTS app_user (
  id BIGINT NOT NULL AUTO_INCREMENT,
  email VARCHAR(190) NOT NULL,
  display_name VARCHAR(120) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_user_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS chat_session (
  id VARCHAR(64) NOT NULL,
  title VARCHAR(200) NOT NULL,
  status VARCHAR(20) NOT NULL,
  owner_email VARCHAR(190),
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS chat_message (
  id BIGINT NOT NULL AUTO_INCREMENT,
  request_id VARCHAR(64),
  session_id VARCHAR(64) NOT NULL,
  role VARCHAR(20) NOT NULL,
  content LONGTEXT NOT NULL,
  status VARCHAR(20) NOT NULL,
  iterations INT,
  tool_calls INT,
  total_tokens INT,
  duration_millis BIGINT,
  created_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_chat_message_session_created (session_id, created_at),
  INDEX idx_chat_message_request (request_id)
);

CREATE TABLE IF NOT EXISTS chat_attachment (
  id VARCHAR(36) NOT NULL,
  owner_email VARCHAR(190) NOT NULL,
  session_id VARCHAR(64) NOT NULL,
  message_id BIGINT,
  original_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(120) NOT NULL,
  size_bytes BIGINT NOT NULL,
  storage_path VARCHAR(500) NOT NULL,
  extracted_text LONGTEXT,
  created_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_attachment_owner_session (owner_email, session_id)
);

CREATE TABLE IF NOT EXISTS chat_message_feedback (
  id BIGINT NOT NULL AUTO_INCREMENT,
  message_id BIGINT NOT NULL,
  owner_email VARCHAR(190) NOT NULL,
  `value` VARCHAR(8) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_feedback_message_owner UNIQUE (message_id, owner_email)
);
