package org.example.bookmanager.service;

import org.example.bookmanager.common.PageResult;
import org.example.bookmanager.dto.BookQueryRequest;
import org.example.bookmanager.dto.BookSaveRequest;
import org.example.bookmanager.entity.Book;

public interface BookService {

    Book create(BookSaveRequest request);

    Book update(Long id, BookSaveRequest request);

    void delete(Long id);

    Book getById(Long id);

    PageResult<Book> page(BookQueryRequest query);
}
