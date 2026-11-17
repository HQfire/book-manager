package org.example.bookmanager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.bookmanager.common.BizException;
import org.example.bookmanager.common.PageResult;
import org.example.bookmanager.dto.BookQueryRequest;
import org.example.bookmanager.dto.BookSaveRequest;
import org.example.bookmanager.entity.Book;
import org.example.bookmanager.mapper.BookMapper;
import org.example.bookmanager.service.BookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class BookServiceImpl implements BookService {

    private static final Logger log = LoggerFactory.getLogger(BookServiceImpl.class);
    private static final String CACHE_KEY_PREFIX = "book:detail:";
    private static final long CACHE_TTL_MINUTES = 30;
    private static final long CACHE_TTL_JITTER_MINUTES = 5;
    private static final long NULL_CACHE_TTL_MINUTES = 1;
    private static final String NULL_MARKER = "__NULL_BOOK__";
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final BookMapper bookMapper;
    private final RedisTemplate<String, String> redisTemplate;

    public BookServiceImpl(BookMapper bookMapper, RedisTemplate<String, String> redisTemplate) {
        this.bookMapper = bookMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    public Book create(BookSaveRequest request, Long currentUserId) {
        Book book = new Book();
        book.setUserId(currentUserId);
        book.setTitle(request.getTitle());
        book.setAuthor(request.getAuthor());
        book.setDescription(request.getDescription());
        bookMapper.insert(book);

        Book saved = bookMapper.selectById(book.getId());
        log.info("book created: id={}, userId={}, title={}", saved.getId(), saved.getUserId(), saved.getTitle());
        return saved;
    }

    @Override
    @Transactional
    public Book update(Long id, BookSaveRequest request, Long currentUserId) {
        Book existing = loadOwnedBook(id, currentUserId);
        existing.setTitle(request.getTitle());
        existing.setAuthor(request.getAuthor());
        existing.setDescription(request.getDescription());
        bookMapper.updateById(existing);
        evictCache(id);

        Book updated = bookMapper.selectById(id);
        log.info("book updated: id={}, userId={}, title={}", updated.getId(), updated.getUserId(), updated.getTitle());
        return updated;
    }

    @Override
    @Transactional
    public void delete(Long id, Long currentUserId) {
        loadOwnedBook(id, currentUserId);
        bookMapper.deleteById(id);
        evictCache(id);
        log.info("book deleted: id={}, userId={}", id, currentUserId);
    }

    @Override
    public Book getById(Long id, Long currentUserId) {
        String key = CACHE_KEY_PREFIX + id;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (NULL_MARKER.equals(cached)) {
                log.info("cache null hit: book id={}", id);
                throw new BizException(404, "book not found");
            }
            try {
                Book cachedBook = MAPPER.readValue(cached, Book.class);
                checkOwnership(cachedBook, currentUserId);
                log.info("cache hit: book id={}", id);
                return cachedBook;
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                log.warn("cache deserialization failed for book id={}, falling back to DB", id);
            }
        }
        log.info("cache miss: book id={}", id);

        Book book = bookMapper.selectById(id);
        if (book == null) {
            cacheNull(key);
            throw new BizException(404, "book not found");
        }
        checkOwnership(book, currentUserId);
        cacheBook(key, book);
        return book;
    }

    @Override
    public PageResult<Book> page(BookQueryRequest query, Long currentUserId) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(query.getPageSize(), MAX_PAGE_SIZE);
        int offset = (pageNum - 1) * pageSize;

        QueryWrapper<Book> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", currentUserId);
        String title = escapeLike(query.getTitle());
        if (!title.isBlank()) {
            wrapper.like("title", title);
        }
        String author = escapeLike(query.getAuthor());
        if (!author.isBlank()) {
            wrapper.like("author", author);
        }
        wrapper.orderByDesc("id");

        Long total = bookMapper.selectCount(wrapper);
        List<Book> records = bookMapper.selectList(wrapper.last("LIMIT " + offset + ", " + pageSize));
        return new PageResult<>(total, pageNum, pageSize, records);
    }

    private Book loadOwnedBook(Long id, Long currentUserId) {
        Book book = bookMapper.selectById(id);
        if (book == null) {
            throw new BizException(404, "book not found");
        }
        checkOwnership(book, currentUserId);
        return book;
    }

    private void checkOwnership(Book book, Long currentUserId) {
        if (book.getUserId() == null || !book.getUserId().equals(currentUserId)) {
            throw new BizException(403, "forbidden: book does not belong to current user");
        }
    }

    private void cacheBook(String key, Book book) {
        long ttlMinutes = CACHE_TTL_MINUTES
                + ThreadLocalRandom.current().nextLong(CACHE_TTL_JITTER_MINUTES + 1);
        try {
            redisTemplate.opsForValue().set(key, MAPPER.writeValueAsString(book), ttlMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("failed to cache book id={}", book.getId(), e);
        }
    }

    private void cacheNull(String key) {
        try {
            redisTemplate.opsForValue().set(key, NULL_MARKER, NULL_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("failed to cache null marker for key={}", key, e);
        }
    }

    private void evictCache(Long id) {
        redisTemplate.delete(CACHE_KEY_PREFIX + id);
    }

    private String escapeLike(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
