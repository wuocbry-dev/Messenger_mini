-- Messenger Mini - MySQL 8.x schema
-- Spring Boot + JPA + Flyway + MySQL + Cloudflare R2 + Agora

CREATE DATABASE IF NOT EXISTS messenger_mini
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE messenger_mini;

CREATE TABLE users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    avatar_object_key VARCHAR(512) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_seen_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE','BLOCKED','DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_refresh_tokens_user (user_id),
    INDEX idx_refresh_tokens_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE conversations (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    type VARCHAR(20) NOT NULL DEFAULT 'DIRECT',
    direct_key VARCHAR(64) NULL,
    title VARCHAR(150) NULL,
    avatar_object_key VARCHAR(512) NULL,
    created_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_conversations_direct_key UNIQUE (direct_key),
    CONSTRAINT fk_conversations_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_conversations_type CHECK (type IN ('DIRECT','GROUP')),
    INDEX idx_conversations_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE conversation_members (
    conversation_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    left_at DATETIME(3) NULL,
    PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_conversation_members_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_conversation_members_role CHECK (role IN ('MEMBER','ADMIN','OWNER')),
    INDEX idx_conversation_members_user (user_id, conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE messages (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT UNSIGNED NOT NULL,
    sender_id BIGINT UNSIGNED NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    content LONGTEXT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_messages_type CHECK (type IN ('TEXT','IMAGE','FILE','SYSTEM')),
    INDEX idx_messages_conversation_time (conversation_id, created_at, id),
    INDEX idx_messages_sender_time (sender_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE message_receipts (
    message_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    delivered_at DATETIME(3) NULL,
    read_at DATETIME(3) NULL,
    PRIMARY KEY (message_id, user_id),
    CONSTRAINT fk_message_receipts_message FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_receipts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_message_receipts_user_read (user_id, read_at),
    INDEX idx_message_receipts_user_delivered (user_id, delivered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE stored_files (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uploader_id BIGINT UNSIGNED NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    checksum_sha256 CHAR(64) NULL,
    etag VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_stored_files_object_key UNIQUE (object_key),
    CONSTRAINT fk_stored_files_uploader FOREIGN KEY (uploader_id) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_stored_files_uploader_time (uploader_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE message_attachments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    message_id BIGINT UNSIGNED NOT NULL,
    file_id BIGINT UNSIGNED NOT NULL,
    attachment_type VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_message_attachments_message FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_attachments_file FOREIGN KEY (file_id) REFERENCES stored_files(id) ON DELETE RESTRICT,
    CONSTRAINT chk_message_attachments_type CHECK (attachment_type IN ('IMAGE','FILE')),
    CONSTRAINT uk_message_attachments_pair UNIQUE (message_id, file_id),
    INDEX idx_message_attachments_file (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE calls (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT UNSIGNED NOT NULL,
    caller_id BIGINT UNSIGNED NOT NULL,
    callee_id BIGINT UNSIGNED NOT NULL,
    call_type VARCHAR(20) NOT NULL DEFAULT 'VOICE',
    status VARCHAR(20) NOT NULL DEFAULT 'RINGING',
    agora_channel_name VARCHAR(128) NOT NULL,
    started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    answered_at DATETIME(3) NULL,
    ended_at DATETIME(3) NULL,
    ended_by BIGINT UNSIGNED NULL,
    end_reason VARCHAR(50) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_calls_agora_channel UNIQUE (agora_channel_name),
    CONSTRAINT fk_calls_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_calls_caller FOREIGN KEY (caller_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_calls_callee FOREIGN KEY (callee_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_calls_ended_by FOREIGN KEY (ended_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_calls_type CHECK (call_type IN ('VOICE','VIDEO')),
    CONSTRAINT chk_calls_status CHECK (status IN ('RINGING','ACCEPTED','REJECTED','MISSED','CANCELLED','ENDED','FAILED')),
    INDEX idx_calls_conversation_time (conversation_id, started_at),
    INDEX idx_calls_caller_time (caller_id, started_at),
    INDEX idx_calls_callee_time (callee_id, started_at),
    INDEX idx_calls_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
