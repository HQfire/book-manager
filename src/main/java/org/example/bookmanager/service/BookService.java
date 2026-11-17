package org.example.bookmanager.service;

import org.example.bookmanager.common.PageResult;
import org.example.bookmanager.dto.BookQueryRequest;
import org.example.bookmanager.dto.BookSaveRequest;
import org.example.bookmanager.dto.ReaderDTO;
import org.example.bookmanager.entity.Book;
import org.example.bookmanager.entity.BookCover;
import org.springframework.web.multipart.MultipartFile;

public interface BookService {

    Book create(BookSaveRequest request, Long currentUserId);

    Book update(Long id, BookSaveRequest request, Long currentUserId);

    void delete(Long id, Long currentUserId);

    Book getById(Long id, Long currentUserId);

    PageResult<Book> page(BookQueryRequest query, Long currentUserId);

    void attachText(Long bookId, Long currentUserId, MultipartFile file);

    void attachCover(Long bookId, Long currentUserId, MultipartFile file);

    ReaderDTO getReader(Long bookId, Long currentUserId);

    BookCover getCover(Long bookId, Long currentUserId);
}
