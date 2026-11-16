package org.example.bookmanager.service.impl;

import org.example.bookmanager.common.BizException;
import org.example.bookmanager.common.PageResult;
import org.example.bookmanager.dto.BookQueryRequest;
import org.example.bookmanager.dto.BookSaveRequest;
import org.example.bookmanager.entity.Book;
import org.example.bookmanager.mapper.BookMapper;
import org.example.bookmanager.service.BookService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookServiceImpl implements BookService {

    private final BookMapper bookMapper;

    public BookServiceImpl(BookMapper bookMapper) {
        this.bookMapper = bookMapper;
    }

    @Override
    public Book create(BookSaveRequest request) {
        Book book = new Book();
        book.setUserId(request.getUserId());
        book.setTitle(request.getTitle());
        book.setAuthor(request.getAuthor());
        book.setDescription(request.getDescription());
        bookMapper.insert(book);
        return book;
    }

    @Override
    public Book update(Long id, BookSaveRequest request) {
        Book existing = bookMapper.selectById(id);
        if (existing == null) {
            throw new BizException(404, "book not found");
        }
        existing.setTitle(request.getTitle());
        existing.setAuthor(request.getAuthor());
        existing.setDescription(request.getDescription());
        bookMapper.updateById(existing);
        return existing;
    }

    @Override
    public void delete(Long id) {
        Book existing = bookMapper.selectById(id);
        if (existing == null) {
            throw new BizException(404, "book not found");
        }
        bookMapper.deleteById(id);
    }

    @Override
    public Book getById(Long id) {
        Book book = bookMapper.selectById(id);
        if (book == null) {
            throw new BizException(404, "book not found");
        }
        return book;
    }

    @Override
    public PageResult<Book> page(BookQueryRequest query) {
        long pageNum = Math.max(query.getPageNum(), 1);
        long pageSize = Math.max(query.getPageSize(), 1);
        long offset = (pageNum - 1) * pageSize;
        List<Book> records = bookMapper.selectPage(query, offset, pageSize);
        long total = bookMapper.countByQuery(query);
        return new PageResult<>(total, pageNum, pageSize, records);
    }
}
