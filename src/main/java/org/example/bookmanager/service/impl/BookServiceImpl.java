package org.example.bookmanager.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.example.bookmanager.common.BizException;
import org.example.bookmanager.common.PageResult;
import org.example.bookmanager.dto.BookQueryRequest;
import org.example.bookmanager.dto.BookSaveRequest;
import org.example.bookmanager.dto.ChapterContent;
import org.example.bookmanager.dto.ChapterToc;
import org.example.bookmanager.dto.ReaderDTO;
import org.example.bookmanager.dto.VolumeToc;
import org.example.bookmanager.entity.Book;
import org.example.bookmanager.entity.BookChapter;
import org.example.bookmanager.entity.BookCover;
import org.example.bookmanager.entity.BookText;
import org.example.bookmanager.mapper.BookChapterMapper;
import org.example.bookmanager.mapper.BookCoverMapper;
import org.example.bookmanager.mapper.BookMapper;
import org.example.bookmanager.mapper.BookTextMapper;
import org.example.bookmanager.service.BookService;
import org.example.bookmanager.service.ChapterParseService;
import org.example.bookmanager.service.ParseStatus;
import org.example.bookmanager.util.ChapterParser;
import org.example.bookmanager.util.TextEncodingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.stream.Collectors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final long MAX_TEXT_SIZE = 5L * 1024 * 1024;
    private static final long MAX_COVER_SIZE = 2L * 1024 * 1024;
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md");
    private static final Set<String> COVER_MIMES = Set.of("image/png", "image/jpeg", "image/webp");

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final BookMapper bookMapper;
    private final BookCoverMapper coverMapper;
    private final BookTextMapper textMapper;
    private final BookChapterMapper chapterMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final ChapterParseService chapterParseService;
    private final Counter cacheHit;
    private final Counter cacheMiss;
    private final Counter cacheEmpty;
    private final Counter chapterLoaded;
    private final Cache<Long, Object> localCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    public BookServiceImpl(BookMapper bookMapper, BookCoverMapper coverMapper,
                           BookTextMapper textMapper, BookChapterMapper chapterMapper,
                           RedisTemplate<String, String> redisTemplate,
                           ChapterParseService chapterParseService,
                           MeterRegistry meterRegistry) {
        this.bookMapper = bookMapper;
        this.coverMapper = coverMapper;
        this.textMapper = textMapper;
        this.chapterMapper = chapterMapper;
        this.redisTemplate = redisTemplate;
        this.chapterParseService = chapterParseService;
        this.cacheHit = meterRegistry.counter("book.cache.hit");
        this.cacheMiss = meterRegistry.counter("book.cache.miss");
        this.cacheEmpty = meterRegistry.counter("book.cache.empty");
        this.chapterLoaded = meterRegistry.counter("book.chapter.loaded");
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
        coverMapper.deleteById(id);
        textMapper.deleteById(id);
        chapterMapper.delete(new QueryWrapper<BookChapter>().eq("book_id", id));
        evictCache(id);
        log.info("book deleted: id={}, userId={}", id, currentUserId);
    }

    @Override
    public Book getById(Long id, Long currentUserId) {
        Object local = localCache.getIfPresent(id);
        if (local != null) {
            if (NULL_MARKER.equals(local)) {
                cacheEmpty.increment();
                log.info("local cache null hit: book id={}", id);
                throw new BizException(404, "book not found");
            }
            Book cachedBook = (Book) local;
            checkOwnership(cachedBook, currentUserId);
            cacheHit.increment();
            log.info("local cache hit: book id={}", id);
            return cachedBook;
        }

        String key = CACHE_KEY_PREFIX + id;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (NULL_MARKER.equals(cached)) {
                cacheEmpty.increment();
                log.info("redis cache null hit: book id={}", id);
                throw new BizException(404, "book not found");
            }
            try {
                Book cachedBook = MAPPER.readValue(cached, Book.class);
                checkOwnership(cachedBook, currentUserId);
                localCache.put(id, cachedBook);
                cacheHit.increment();
                log.info("redis cache hit: book id={}", id);
                return cachedBook;
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                log.warn("cache deserialization failed for book id={}, falling back to DB", id);
            }
        }
        cacheMiss.increment();
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
        String keyword = query.getKeyword() == null ? "" : query.getKeyword().trim();
        String booleanQuery = buildBooleanQuery(keyword);
        if (!booleanQuery.isBlank()) {
            wrapper.apply("MATCH(title, author) AGAINST ({0} IN BOOLEAN MODE)", booleanQuery);
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
        localCache.put(book.getId(), book);
        long ttlMinutes = CACHE_TTL_MINUTES
                + ThreadLocalRandom.current().nextLong(CACHE_TTL_JITTER_MINUTES + 1);
        try {
            redisTemplate.opsForValue().set(key, MAPPER.writeValueAsString(book), ttlMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("failed to cache book id={}", book.getId(), e);
        }
    }

    private void cacheNull(String key) {
        localCache.put(extractId(key), NULL_MARKER);
        try {
            redisTemplate.opsForValue().set(key, NULL_MARKER, NULL_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("failed to cache null marker for key={}", key, e);
        }
    }

    private void evictCache(Long id) {
        localCache.invalidate(id);
        redisTemplate.delete(CACHE_KEY_PREFIX + id);
    }

    private Long extractId(String key) {
        return Long.valueOf(key.substring(CACHE_KEY_PREFIX.length()));
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

    private String buildBooleanQuery(String keyword) {
        String sanitized = keyword.replaceAll("[+-><()*~@\"]", " ");
        return Arrays.stream(sanitized.split("\\s+"))
                .filter(token -> !token.isBlank())
                .map(token -> "+" + token)
                .collect(Collectors.joining(" "));
    }

    @Override
    @Transactional
    public void attachText(Long bookId, Long currentUserId, MultipartFile file) {
        loadOwnedBook(bookId, currentUserId);
        validateTextFile(file);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BizException(400, "failed to read uploaded text file");
        }
        TextEncodingUtil.Decoded decoded = TextEncodingUtil.decode(bytes);

        BookText entity = textMapper.selectById(bookId);
        boolean insert = entity == null;
        if (insert) {
            entity = new BookText();
            entity.setBookId(bookId);
        }
        entity.setFileName(file.getOriginalFilename());
        entity.setEncoding(decoded.encoding());
        entity.setSize(file.getSize());
        entity.setContent(decoded.content());
        entity.setParseStatus(ParseStatus.PENDING);
        if (insert) {
            textMapper.insert(entity);
        } else {
            textMapper.updateById(entity);
        }
        chapterParseService.parseChapters(bookId, decoded.content());
        log.info("text attached, chapter parsing scheduled: bookId={}, fileName={}, encoding={}, size={}",
                bookId, entity.getFileName(), entity.getEncoding(), entity.getSize());
    }

    @Override
    @Transactional
    public void attachCover(Long bookId, Long currentUserId, MultipartFile file) {
        loadOwnedBook(bookId, currentUserId);
        validateCoverFile(file);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BizException(400, "failed to read uploaded cover");
        }

        BookCover entity = coverMapper.selectById(bookId);
        boolean insert = entity == null;
        if (insert) {
            entity = new BookCover();
            entity.setBookId(bookId);
        }
        entity.setFileName(file.getOriginalFilename());
        entity.setMime(file.getContentType());
        entity.setSize(file.getSize());
        entity.setData(bytes);
        if (insert) {
            coverMapper.insert(entity);
        } else {
            coverMapper.updateById(entity);
        }
        log.info("cover attached: bookId={}, fileName={}, mime={}, size={}",
                bookId, entity.getFileName(), entity.getMime(), entity.getSize());
    }

    @Override
    public ReaderDTO getReader(Long bookId, Long currentUserId) {
        loadOwnedBook(bookId, currentUserId);
        BookText text = textMapper.selectById(bookId);
        if (text == null || text.getContent() == null) {
            throw new BizException(404, "no text uploaded for this book");
        }
        String status = text.getParseStatus() == null ? ParseStatus.DONE : text.getParseStatus();
        if (ParseStatus.PENDING.equals(status) || ParseStatus.FAILED.equals(status)) {
            return new ReaderDTO(text.getFileName(), text.getEncoding(), text.getSize(), status, List.of(), List.of());
        }
        ensureChapters(bookId, text.getContent());
        List<BookChapter> rows = chapterMapper.selectList(
                new QueryWrapper<BookChapter>().eq("book_id", bookId).orderByAsc("chapter_no"));
        List<ChapterToc> toc = rows.stream()
                .map(row -> new ChapterToc(row.getChapterNo(), row.getTitle()))
                .toList();
        List<VolumeToc> volumes = groupByVolume(rows);
        return new ReaderDTO(text.getFileName(), text.getEncoding(), text.getSize(), ParseStatus.DONE, toc, volumes);
    }

    private List<VolumeToc> groupByVolume(List<BookChapter> rows) {
        boolean hasVolume = rows.stream()
                .anyMatch(row -> row.getVolumeTitle() != null && !row.getVolumeTitle().isBlank());
        if (!hasVolume) {
            return List.of();
        }
        Map<String, List<ChapterToc>> grouped = new LinkedHashMap<>();
        for (BookChapter row : rows) {
            String key = row.getVolumeTitle() == null ? "" : row.getVolumeTitle();
            ChapterToc toc = new ChapterToc(row.getChapterNo(), row.getTitle());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(toc);
        }
        return grouped.entrySet().stream()
                .map(entry -> new VolumeToc(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public ChapterContent getChapter(Long bookId, int chapterIndex, Long currentUserId) {
        loadOwnedBook(bookId, currentUserId);
        BookChapter row = chapterMapper.selectOne(new QueryWrapper<BookChapter>()
                .eq("book_id", bookId).eq("chapter_no", chapterIndex));
        if (row == null) {
            throw new BizException(404, "chapter not found");
        }
        chapterLoaded.increment();
        return new ChapterContent(row.getChapterNo(), row.getTitle(), row.getContent());
    }

    private void replaceChapters(Long bookId, String content) {
        chapterMapper.delete(new QueryWrapper<BookChapter>().eq("book_id", bookId));
        List<ChapterParser.Chapter> chapters = ChapterParser.parse(content);
        for (ChapterParser.Chapter chapter : chapters) {
            BookChapter row = new BookChapter();
            row.setBookId(bookId);
            row.setChapterNo(chapter.index());
            row.setTitle(chapter.title());
            row.setVolumeTitle(chapter.volumeTitle());
            row.setContent(chapter.content());
            chapterMapper.insert(row);
        }
    }

    private void ensureChapters(Long bookId, String sourceContent) {
        Long count = chapterMapper.selectCount(new QueryWrapper<BookChapter>().eq("book_id", bookId));
        if (count != null && count > 0) {
            return;
        }
        if (sourceContent == null || sourceContent.isBlank()) {
            return;
        }
        replaceChapters(bookId, sourceContent);
    }

    @Override
    public BookCover getCover(Long bookId, Long currentUserId) {
        loadOwnedBook(bookId, currentUserId);
        BookCover cover = coverMapper.selectById(bookId);
        if (cover == null || cover.getData() == null) {
            throw new BizException(404, "cover not found");
        }
        return cover;
    }

    private void validateTextFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "text file is required");
        }
        if (!TEXT_EXTENSIONS.contains(extensionOf(file.getOriginalFilename()))) {
            throw new BizException(400, "only txt and md plain text files are supported");
        }
        if (file.getSize() > MAX_TEXT_SIZE) {
            throw new BizException(400, "text file exceeds 5MB limit");
        }
    }

    private void validateCoverFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "cover image is required");
        }
        if (!COVER_MIMES.contains(file.getContentType())) {
            throw new BizException(400, "only png, jpg and webp cover images are supported");
        }
        if (file.getSize() > MAX_COVER_SIZE) {
            throw new BizException(400, "cover image exceeds 2MB limit");
        }
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }
}
