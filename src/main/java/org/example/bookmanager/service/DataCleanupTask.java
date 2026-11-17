package org.example.bookmanager.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.bookmanager.entity.Book;
import org.example.bookmanager.entity.BookChapter;
import org.example.bookmanager.entity.BookCover;
import org.example.bookmanager.entity.BookText;
import org.example.bookmanager.mapper.BookChapterMapper;
import org.example.bookmanager.mapper.BookCoverMapper;
import org.example.bookmanager.mapper.BookMapper;
import org.example.bookmanager.mapper.BookTextMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DataCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(DataCleanupTask.class);

    private final BookMapper bookMapper;
    private final BookCoverMapper coverMapper;
    private final BookTextMapper textMapper;
    private final BookChapterMapper chapterMapper;

    public DataCleanupTask(BookMapper bookMapper, BookCoverMapper coverMapper,
                           BookTextMapper textMapper, BookChapterMapper chapterMapper) {
        this.bookMapper = bookMapper;
        this.coverMapper = coverMapper;
        this.textMapper = textMapper;
        this.chapterMapper = chapterMapper;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void scheduledCleanup() {
        int deleted = cleanupOrphans();
        if (deleted > 0) {
            log.info("orphan attachment cleanup removed {} rows", deleted);
        }
    }

    @Transactional
    public int cleanupOrphans() {
        Set<Long> bookIds = bookMapper.selectList(null).stream()
                .map(Book::getId)
                .collect(Collectors.toSet());

        int deleted = 0;
        List<Long> orphanCovers = coverMapper.selectList(null).stream()
                .map(BookCover::getBookId)
                .filter(id -> !bookIds.contains(id))
                .toList();
        if (!orphanCovers.isEmpty()) {
            coverMapper.deleteBatchIds(orphanCovers);
            deleted += orphanCovers.size();
        }

        List<Long> orphanTexts = textMapper.selectList(null).stream()
                .map(BookText::getBookId)
                .filter(id -> !bookIds.contains(id))
                .toList();
        if (!orphanTexts.isEmpty()) {
            textMapper.deleteBatchIds(orphanTexts);
            deleted += orphanTexts.size();
        }

        List<Long> orphanChapters = chapterMapper.selectList(null).stream()
                .map(BookChapter::getBookId)
                .filter(id -> !bookIds.contains(id))
                .distinct()
                .toList();
        if (!orphanChapters.isEmpty()) {
            chapterMapper.delete(new QueryWrapper<BookChapter>().in("book_id", orphanChapters));
            deleted += orphanChapters.size();
        }
        return deleted;
    }
}
