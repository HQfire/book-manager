package org.example.bookmanager.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChapterParser {

    private static final Pattern VOLUME_PREFIX = Pattern.compile(
            "^(第\\s*[0-9零一二三四五六七八九十百千]+\\s*[卷巻册])\\b(.*)$");

    private static final String CHAPTER_BODY =
            "(?:第\\s*[0-9零一二三四五六七八九十百千]+\\s*[章节回话幕回][^\\n]{0,40}"
                    + "|Chapter\\s+\\d+[^\\n]{0,40}"
                    + "|(?:序章|序幕|终章|番外篇|番外|特典|后记|幕间|短篇|Intermission)[^\\n]{0,40})";

    private static final Pattern CHAPTER = Pattern.compile(CHAPTER_BODY, Pattern.CASE_INSENSITIVE);

    private static final Pattern CHAPTER_START = Pattern.compile("^" + CHAPTER_BODY,
            Pattern.CASE_INSENSITIVE);

    private ChapterParser() {
    }

    public record Chapter(int index, String title, String volumeTitle, String content) {
    }

    public static List<Chapter> parse(String text) {
        List<Chapter> chapters = new ArrayList<>();
        if (text == null || text.isBlank()) {
            chapters.add(new Chapter(0, "正文", null, ""));
            return chapters;
        }

        String[] lines = text.split("\r?\n", -1);
        String prologue = "";
        String currentVolume = null;
        String currentTitle = null;
        StringBuilder currentBody = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            Matcher volumeMatcher = VOLUME_PREFIX.matcher(trimmed);
            boolean hasVolumePrefix = volumeMatcher.find();
            boolean chapterHeading;
            if (hasVolumePrefix) {
                String rest = volumeMatcher.group(2).trim();
                chapterHeading = CHAPTER.matcher(rest).find();
            } else {
                chapterHeading = CHAPTER_START.matcher(trimmed).find();
            }

            if (chapterHeading) {
                if (currentTitle != null) {
                    chapters.add(build(chapters.size(), currentTitle, currentVolume, currentBody.toString()));
                } else if (currentBody.toString().trim().length() > 0 && prologue.isEmpty()) {
                    prologue = currentBody.toString().trim();
                }
                if (hasVolumePrefix) {
                    currentVolume = volumeMatcher.group(1).trim();
                }
                currentTitle = trimmed;
                currentBody = new StringBuilder();
            } else if (hasVolumePrefix) {
                if (currentTitle != null) {
                    chapters.add(build(chapters.size(), currentTitle, currentVolume, currentBody.toString()));
                } else if (currentBody.toString().trim().length() > 0) {
                    prologue = currentBody.toString().trim();
                }
                currentVolume = trimmed;
                currentTitle = null;
                currentBody = new StringBuilder();
            } else {
                currentBody.append(line).append("\n");
            }
        }

        if (currentTitle == null) {
            chapters.add(new Chapter(0, "正文", currentVolume, String.join("\n", lines).trim()));
            return chapters;
        }

        String body = currentBody.toString().trim();
        if (!prologue.isEmpty()) {
            body = prologue + "\n\n" + body;
        }
        chapters.add(build(chapters.size(), currentTitle, currentVolume, body));
        return chapters;
    }

    private static Chapter build(int index, String title, String volume, String body) {
        return new Chapter(index, title, volume, body.trim());
    }
}
