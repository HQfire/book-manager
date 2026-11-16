package org.example.bookmanager.service;

import org.example.bookmanager.common.PageResult;
import org.example.bookmanager.dto.BookQueryRequest;
import org.example.bookmanager.dto.BookSaveRequest;
import org.example.bookmanager.entity.Book;

public interface BookService {

    Book create(BookSaveRequest request, Long currentUserId);

    Book update(Long id, BookSaveRequest request, Long currentUserId);

    void delete(Long id, Long currentUserId);

    Book getById(Long id, Long currentUserId);

    PageResult<Book> page(BookQueryRequest query, Long currentUserId);
}
