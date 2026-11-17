package org.example.bookmanager.util;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class TextEncodingUtil {

    private static final Charset GB18030 = Charset.forName("GB18030");

    private TextEncodingUtil() {
    }

    public record Decoded(String encoding, String content) {
    }

    public static Decoded decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new Decoded(StandardCharsets.UTF_8.name(), "");
        }

        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            return new Decoded(StandardCharsets.UTF_8.name(),
                    new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8));
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
            return new Decoded("UTF-16LE",
                    new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE));
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
            return new Decoded("UTF-16BE",
                    new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE));
        }

        if (canDecodeStrict(bytes, StandardCharsets.UTF_8)) {
            return new Decoded(StandardCharsets.UTF_8.name(), new String(bytes, StandardCharsets.UTF_8));
        }

        String utf16Encoding = detectUtf16WithoutBom(bytes);
        if (utf16Encoding != null) {
            Charset charset = "UTF-16LE".equals(utf16Encoding) ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_16BE;
            return new Decoded(utf16Encoding, new String(bytes, charset));
        }

        return new Decoded("GB18030", new String(bytes, GB18030));
    }

    private static boolean canDecodeStrict(byte[] bytes, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(java.nio.ByteBuffer.wrap(bytes));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String detectUtf16WithoutBom(byte[] bytes) {
        int nullAtEven = 0;
        int nullAtOdd = 0;
        int sample = Math.min(bytes.length, 1024);
        for (int i = 0; i < sample; i++) {
            if (bytes[i] != 0) {
                continue;
            }
            if (i % 2 == 0) {
                nullAtEven++;
            } else {
                nullAtOdd++;
            }
        }
        if (nullAtOdd > sample / 8 && nullAtOdd > nullAtEven * 2) {
            return "UTF-16LE";
        }
        if (nullAtEven > sample / 8 && nullAtEven > nullAtOdd * 2) {
            return "UTF-16BE";
        }
        return null;
    }
}
