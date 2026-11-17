package org.example.bookmanager.dto;

import org.example.bookmanager.util.ChapterParser.Chapter;

import java.util.List;

public record ReaderDTO(String fileName, String encoding, long size, List<Chapter> chapters) {
}
