-- Apply this only to an existing database that still has the old single-column index.
-- Fresh databases already create idx_book_user_id_id from schema.sql.

ALTER TABLE book DROP KEY idx_book_user_id;
ALTER TABLE book ADD KEY idx_book_user_id_id (user_id, id);
