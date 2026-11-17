package org.example.bookmanager.util;

import org.example.bookmanager.util.TextEncodingUtil.Decoded;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextEncodingUtilTest {

    @Test
    void detectsPlainUtf8() {
        byte[] bytes = "你好，世界".getBytes(StandardCharsets.UTF_8);
        Decoded decoded = TextEncodingUtil.decode(bytes);
        assertEquals("UTF-8", decoded.encoding());
        assertEquals("你好，世界", decoded.content());
    }

    @Test
    void detectsUtf8Bom() {
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[body.length + 3];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(body, 0, bytes, 3, body.length);
        Decoded decoded = TextEncodingUtil.decode(bytes);
        assertEquals("UTF-8", decoded.encoding());
        assertEquals("hello", decoded.content());
    }

    @Test
    void fallsBackToGb18030() {
        byte[] bytes = "测试中文".getBytes(Charset.forName("GB18030"));
        Decoded decoded = TextEncodingUtil.decode(bytes);
        assertEquals("GB18030", decoded.encoding());
        assertEquals("测试中文", decoded.content());
    }

    @Test
    void detectsUtf16LeBom() {
        byte[] body = "abc".getBytes(StandardCharsets.UTF_16LE);
        byte[] bytes = new byte[body.length + 2];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xFE;
        System.arraycopy(body, 0, bytes, 2, body.length);
        Decoded decoded = TextEncodingUtil.decode(bytes);
        assertEquals("UTF-16LE", decoded.encoding());
        assertEquals("abc", decoded.content());
    }
}
