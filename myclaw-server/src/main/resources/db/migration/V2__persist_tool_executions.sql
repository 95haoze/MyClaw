CREATE TABLE IF NOT EXISTS chat_tool_execution (
  id BIGINT NOT NULL AUTO_INCREMENT,
  message_id BIGINT NOT NULL,
  execution_id VARCHAR(128) NOT NULL,
  tool_name VARCHAR(120) NOT NULL,
  arguments_json LONGTEXT NOT NULL,
  status VARCHAR(20) NOT NULL,
  duration_millis BIGINT NOT NULL,
  result_text LONGTEXT NOT NULL,
  sequence_no INT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_tool_execution_message_sequence (message_id, sequence_no),
  CONSTRAINT fk_tool_execution_message FOREIGN KEY (message_id) REFERENCES chat_message(id) ON DELETE CASCADE
);
