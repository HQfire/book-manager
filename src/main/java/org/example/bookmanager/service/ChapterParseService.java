package org.example.bookmanager.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.bookmanager.entity.BookChapter;
import org.example.bookmanager.entity.BookText;
import org.example.bookmanager.mapper.BookChapterMapper;
import org.example.bookmanager.mapper.BookTextMapper;
import org.example.bookmanager.util.ChapterParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChapterParseService {

    private static final Logger log = LoggerFactory.getLogger(ChapterParseService.class);

    private final BookChapterMapper chapterMapper;
    private final BookTextMapper textMapper;

    public ChapterParseService(BookChapterMapper chapterMapper, BookTextMapper textMapper) {
        this.chapterMapper = chapterMapper;
        this.textMapper = textMapper;
    }

    @Async("chapterParseExecutor")
    @Transactional
    public void parseChapters(Long bookId, String content) {
        try {
            chapterMapper.delete(new QueryWrapper<BookChapter>().eq("book_id", bookId));
            for (ChapterParser.Chapter chapter : ChapterParser.parse(content)) {
                BookChapter row = new BookChapter();
                row.setBookId(bookId);
                row.setChapterNo(chapter.index());
                row.setTitle(chapter.title());
                row.setVolumeTitle(chapter.volumeTitle());
                row.setContent(chapter.content());
                chapterMapper.insert(row);
            }
            markStatus(bookId, ParseStatus.DONE);
            log.info("async chapters parsed: bookId={}", bookId);
        } catch (Exception e) {
            chapterMapper.delete(new QueryWrapper<BookChapter>().eq("book_id", bookId));
            markStatus(bookId, ParseStatus.FAILED);
            log.error("async chapter parse failed: bookId={}", bookId, e);
        }
    }

    private void markStatus(Long bookId, String status) {
        BookText text = textMapper.selectById(bookId);
        if (text != null) {
            text.setParseStatus(status);
            textMapper.updateById(text);
        }
    }
}
