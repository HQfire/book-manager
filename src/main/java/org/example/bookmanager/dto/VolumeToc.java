package org.example.bookmanager.dto;

import java.util.List;

public record VolumeToc(String title, List<ChapterToc> chapters) {
}
