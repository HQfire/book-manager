-- Per-chapter content split out of book_text so the reader can load one chapter on demand.

CREATE TABLE IF NOT EXISTS book_chapter (
    id BIGINT NOT NULL AUTO_INCREMENT,
    book_id BIGINT NOT NULL,
    chapter_no INT NOT NULL,
    title VARCHAR(255),
    content LONGTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_chapter_book_no (book_id, chapter_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
