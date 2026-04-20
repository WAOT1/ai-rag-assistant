CREATE DATABASE IF NOT EXISTS rag_kb DEFAULT CHARACTER SET utf8mb4;
USE rag_kb;

-- 文档表
CREATE TABLE IF NOT EXISTS document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL COMMENT '文档标题',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_type VARCHAR(50) COMMENT '文件类型 pdf/word/txt',
    file_size BIGINT COMMENT '文件大尋(字节)',
    content TEXT COMMENT '文档全文',
    status TINYINT DEFAULT 1 COMMENT '0-禁用 1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档';

-- 文档切片表
CREATE TABLE IF NOT EXISTS document_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL COMMENT '所属文档ID',
    chunk_index INT NOT NULL COMMENT '片段序号',
    content TEXT NOT NULL COMMENT '片段内容',
    embedding_json TEXT COMMENT '向量JSON [0.1, 0.2, ...]',
    char_start INT COMMENT '在原文档中的开始位置',
    char_end INT COMMENT '在原文档中的结束位置',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (document_id) REFERENCES document(id) ON DELETE CASCADE,
    INDEX idx_document (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档切片及向量';

-- 对话历史表
CREATE TABLE IF NOT EXISTS chat_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL COMMENT '对话会话ID',
    role VARCHAR(20) NOT NULL COMMENT 'user/assistant',
    content TEXT NOT NULL COMMENT '消息内容',
    sources TEXT COMMENT '引用来源JSON',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话历史';
