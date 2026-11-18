package org.example.bookmanager.dto;

import java.util.List;

public record ReaderDTO(String fileName, String encoding, long size, String parseStatus,
                        List<ChapterToc> chapters) {
}
