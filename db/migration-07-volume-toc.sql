-- Group chapters under volumes for light novels released per volume.
-- Books without volume headings keep a flat chapter list; volume_title stays NULL.

ALTER TABLE book_chapter
    ADD COLUMN volume_title VARCHAR(255) NULL;
