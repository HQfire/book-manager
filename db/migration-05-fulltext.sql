-- Fulltext index over title and author with the ngram parser for Chinese-friendly keyword match.

ALTER TABLE book
    ADD FULLTEXT INDEX ft_book_title_author (title, author) WITH PARSER ngram;
