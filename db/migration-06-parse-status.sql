-- Track asynchronous chapter parsing state for each uploaded source text.

ALTER TABLE book_text
    ADD COLUMN parse_status VARCHAR(20) NOT NULL DEFAULT 'DONE';
