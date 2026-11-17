package org.example.bookmanager.util;

import org.example.bookmanager.util.ChapterParser.Chapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChapterParserTest {

    @Test
    void splitsChineseChapters() {
        String text = "第一章 启程\n少年走进森林。\n\n第二章 归途\n少年回到村庄。";
        List<Chapter> chapters = ChapterParser.parse(text);
        assertEquals(2, chapters.size());
        assertEquals("第一章 启程", chapters.get(0).title());
        assertTrue(chapters.get(0).content().contains("少年走进森林"));
        assertEquals("第二章 归途", chapters.get(1).title());
        assertTrue(chapters.get(1).content().contains("少年回到村庄"));
    }

    @Test
    void mergesPrologueIntoFirstChapter() {
        String text = "引子文字。\n\n第一章 开篇\n正文内容。";
        List<Chapter> chapters = ChapterParser.parse(text);
        assertEquals(1, chapters.size());
        assertTrue(chapters.get(0).content().contains("引子文字"));
        assertTrue(chapters.get(0).content().contains("正文内容"));
    }

    @Test
    void treatsPlainTextAsSingleChapter() {
        String text = "没有任何章节标题的纯文本内容。";
        List<Chapter> chapters = ChapterParser.parse(text);
        assertEquals(1, chapters.size());
        assertEquals("正文", chapters.get(0).title());
        assertTrue(chapters.get(0).content().contains("纯文本"));
    }

    @Test
    void splitsEnglishChapterHeading() {
        String text = "Chapter 1 Start\nBody one.\n\nChapter 2 End\nBody two.";
        List<Chapter> chapters = ChapterParser.parse(text);
        assertEquals(2, chapters.size());
        assertEquals("Chapter 1 Start", chapters.get(0).title());
    }
}
