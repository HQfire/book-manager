-- Attachments (cover image BLOB and decoded plain text) for an existing database.

CREATE TABLE IF NOT EXISTS book_cover (
    book_id BIGINT NOT NULL PRIMARY KEY,
    file_name VARCHAR(255),
    mime VARCHAR(50),
    size BIGINT,
    data LONGBLOB,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS book_text (
    book_id BIGINT NOT NULL PRIMARY KEY,
    file_name VARCHAR(255),
    encoding VARCHAR(50),
    size BIGINT,
    content LONGTEXT,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
