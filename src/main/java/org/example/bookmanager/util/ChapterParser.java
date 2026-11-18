package org.example.bookmanager.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChapterParser {

    private static final Pattern HEADING = Pattern.compile(
            "^(?:第\\s*[0-9零一二三四五六七八九十百千]+\\s*[章节卷回][^\\n]{0,40}"
                    + "|Chapter\\s+\\d+[^\\n]{0,40})",
            Pattern.CASE_INSENSITIVE);

    private ChapterParser() {
    }

    public record Chapter(int index, String title, String content) {
    }

    public static List<Chapter> parse(String text) {
        List<Chapter> chapters = new ArrayList<>();
        if (text == null || text.isBlank()) {
            chapters.add(new Chapter(0, "正文", ""));
            return chapters;
        }

        String[] lines = text.split("\r?\n", -1);
        String prologue = "";
        String currentTitle = null;
        StringBuilder currentBody = new StringBuilder();

        for (String line : lines) {
            Matcher matcher = HEADING.matcher(line.trim());
            if (matcher.find()) {
                if (currentTitle != null) {
                    chapters.add(build(chapters.size(), currentTitle, currentBody.toString()));
                } else if (currentBody.toString().trim().length() > 0) {
                    prologue = currentBody.toString().trim();
                }
                currentTitle = line.trim();
                currentBody = new StringBuilder();
            } else {
                currentBody.append(line).append("\n");
            }
        }

        if (currentTitle == null) {
            chapters.add(new Chapter(0, "正文", String.join("\n", lines).trim()));
            return chapters;
        }

        String body = currentBody.toString().trim();
        if (!prologue.isEmpty()) {
            body = prologue + "\n\n" + body;
        }
        chapters.add(build(chapters.size(), currentTitle, body));
        return chapters;
    }

    private static Chapter build(int index, String title, String body) {
        return new Chapter(index, title, body.trim());
    }
}
