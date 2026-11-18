package org.example.bookmanager.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.bookmanager.entity.Book;
import org.example.bookmanager.entity.UserAccount;
import org.example.bookmanager.mapper.BookChapterMapper;
import org.example.bookmanager.mapper.BookCoverMapper;
import org.example.bookmanager.mapper.BookMapper;
import org.example.bookmanager.mapper.BookTextMapper;
import org.example.bookmanager.mapper.UserAccountMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private BookMapper bookMapper;

    @Autowired
    private BookCoverMapper coverMapper;

    @Autowired
    private BookTextMapper textMapper;

    @Autowired
    private BookChapterMapper chapterMapper;

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final List<String> createdUsernames = new ArrayList<>();
    private final List<Long> createdBookIds = new ArrayList<>();
    private final List<String> cacheKeys = new ArrayList<>();

    private String newUserToken() throws Exception {
        String username = "it_" + System.nanoTime();
        createdUsernames.add(username);
        register(username, "secret123");
        return login(username, "secret123");
    }

    private void register(String username, String password) throws Exception {
        String body = OBJECT_MAPPER.writeValueAsString(Map.of("username", username, "password", password));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private JsonNode loginJson(String username, String password) throws Exception {
        String body = OBJECT_MAPPER.writeValueAsString(Map.of("username", username, "password", password));
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    }

    private String login(String username, String password) throws Exception {
        return loginJson(username, password).path("data").path("token").asText();
    }

    private Long createBook(String token, String title) throws Exception {
        String body = OBJECT_MAPPER.writeValueAsString(Map.of(
                "title", title, "author", "Tester", "description", "integration test"));
        MvcResult result = mockMvc.perform(post("/api/books")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode node = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        Long id = node.path("data").path("id").asLong();
        createdBookIds.add(id);
        return id;
    }

    @Test
    void shouldRejectRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/books/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void shouldRejectInvalidToken() throws Exception {
        mockMvc.perform(get("/api/books/page").header("Authorization", "Bearer not-a-valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void shouldRejectDuplicateUsername() throws Exception {
        String username = "it_dup_" + System.nanoTime();
        createdUsernames.add(username);
        register(username, "secret123");
        String body = OBJECT_MAPPER.writeValueAsString(Map.of("username", username, "password", "secret123"));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void shouldRejectWrongPassword() throws Exception {
        String username = "it_bad_" + System.nanoTime();
        createdUsernames.add(username);
        register(username, "secret123");
        String body = OBJECT_MAPPER.writeValueAsString(Map.of("username", username, "password", "wrong-pass"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void shouldLockAfterRepeatedFailedLogins() throws Exception {
        String username = "it_lock_" + System.nanoTime();
        createdUsernames.add(username);
        register(username, "secret123");
        String failKey = "auth:login:fail:" + username;

        for (int i = 0; i < 5; i++) {
            String body = OBJECT_MAPPER.writeValueAsString(Map.of("username", username, "password", "wrong-pass"));
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(401));
        }

        String blockedBody = OBJECT_MAPPER.writeValueAsString(Map.of("username", username, "password", "wrong-pass"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockedBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(429));

        redisTemplate.delete(failKey);
    }

    @Test
    void shouldRejectBlankTitle() throws Exception {
        String token = newUserToken();
        String body = OBJECT_MAPPER.writeValueAsString(Map.of("title", "", "author", "Tester"));
        mockMvc.perform(post("/api/books")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void shouldReturn404ForMissingBook() throws Exception {
        String token = newUserToken();
        mockMvc.perform(get("/api/books/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void shouldEnforceBookOwnership() throws Exception {
        String ownerToken = newUserToken();
        String otherToken = newUserToken();
        Long bookId = createBook(ownerToken, "Owned Book");

        mockMvc.perform(get("/api/books/" + bookId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        String updateBody = OBJECT_MAPPER.writeValueAsString(Map.of(
                "title", "Hijacked", "author", "Other", "description", "x"));
        mockMvc.perform(put("/api/books/" + bookId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(delete("/api/books/" + bookId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/books/" + bookId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldMatchKeywordAgainstTitleOrAuthor() throws Exception {
        String token = newUserToken();
        createBook(token, "Alpha Title");
        String body = OBJECT_MAPPER.writeValueAsString(Map.of(
                "title", "Beta Title", "author", "UniqueAuthor", "description", "x"));
        MvcResult result = mockMvc.perform(post("/api/books")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        createdBookIds.add(OBJECT_MAPPER.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong());

        mockMvc.perform(get("/api/books/page?keyword=Alpha")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/books/page?keyword=UniqueAuthor")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void shouldRevokeTokenAfterLogout() throws Exception {
        String token = newUserToken();
        mockMvc.perform(get("/api/books/page").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/books/page").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void shouldRotateRefreshTokenAndRejectOldRefresh() throws Exception {
        String username = "it_refresh_" + System.nanoTime();
        createdUsernames.add(username);
        register(username, "secret123");
        JsonNode session = loginJson(username, "secret123");
        String oldRefresh = session.path("data").path("refreshToken").asText();
        assertFalse(oldRefresh.isBlank());

        String refreshBody = OBJECT_MAPPER.writeValueAsString(Map.of("refreshToken", oldRefresh));
        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode refreshedNode = OBJECT_MAPPER.readTree(refreshed.getResponse().getContentAsString());
        String newRefresh = refreshedNode.path("data").path("refreshToken").asText();
        assertFalse(newRefresh.isBlank());
        assertNotEquals(oldRefresh, newRefresh);

        String oldBody = OBJECT_MAPPER.writeValueAsString(Map.of("refreshToken", oldRefresh));
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oldBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void shouldCacheDetailAndEvictOnUpdate() throws Exception {
        String token = newUserToken();
        Long bookId = createBook(token, "Cache Probe");
        String key = "book:detail:" + bookId;

        mockMvc.perform(get("/api/books/" + bookId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(key)));
        cacheKeys.add(key);

        mockMvc.perform(get("/api/books/" + bookId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String updateBody = OBJECT_MAPPER.writeValueAsString(Map.of(
                "title", "Cache Probe Updated", "author", "Tester", "description", "x"));
        mockMvc.perform(put("/api/books/" + bookId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(key)));
    }

    @Test
    void shouldCacheNullMarkerForMissingBook() throws Exception {
        String token = newUserToken();
        String missingKey = "book:detail:99999999";
        mockMvc.perform(get("/api/books/99999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(missingKey)));
        cacheKeys.add(missingKey);
    }

    @Test
    void shouldClampPageSizeToMax() throws Exception {
        String token = newUserToken();
        mockMvc.perform(get("/api/books/page?pageSize=100").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.pageSize").value(50));
    }

    @Test
    void shouldUploadTextAndParseChapters() throws Exception {
        String token = newUserToken();
        Long bookId = createBook(token, "Reader Probe");
        String content = "第一章 启程\n少年走进森林。\n\n第二章 归途\n少年回到村庄。";
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/books/" + bookId + "/text")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        JsonNode node = null;
        String status = "PENDING";
        for (int i = 0; i < 20; i++) {
            MvcResult result = mockMvc.perform(get("/api/books/" + bookId + "/reader")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn();
            node = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
            status = node.path("data").path("parseStatus").asText();
            if (!"PENDING".equals(status)) {
                break;
            }
            Thread.sleep(300);
        }
        assertEquals("DONE", status);
        assertEquals("UTF-8", node.path("data").path("encoding").asText());
        assertEquals(2, node.path("data").path("chapters").size());
        assertEquals(0, node.path("data").path("volumes").size());
        assertEquals("第一章 启程", node.path("data").path("chapters").get(0).path("title").asText());

        MvcResult chapterResult = mockMvc.perform(get("/api/books/" + bookId + "/chapters/0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode chapter = OBJECT_MAPPER.readTree(chapterResult.getResponse().getContentAsString());
        assertTrue(chapter.path("data").path("content").asText().contains("少年走进森林"));
    }

    @Test
    void shouldGroupChaptersByVolume() throws Exception {
        String token = newUserToken();
        Long bookId = createBook(token, "Volume Probe");
        String content = "第一卷 启程篇\n第一章 相遇\n少年相遇。\n\n第二章 同行\n两人同行。\n\n第二卷 旅途篇\n第三章 分别\n少年离去。";
        MockMultipartFile file = new MockMultipartFile(
                "file", "light-novel.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/books/" + bookId + "/text")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        JsonNode node = null;
        String status = "PENDING";
        for (int i = 0; i < 20; i++) {
            MvcResult result = mockMvc.perform(get("/api/books/" + bookId + "/reader")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn();
            node = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
            status = node.path("data").path("parseStatus").asText();
            if (!"PENDING".equals(status)) {
                break;
            }
            Thread.sleep(300);
        }
        assertEquals("DONE", status);
        assertEquals(3, node.path("data").path("chapters").size());
        assertEquals(2, node.path("data").path("volumes").size());
        assertEquals("第一卷 启程篇", node.path("data").path("volumes").get(0).path("title").asText());
        assertEquals(2, node.path("data").path("volumes").get(0).path("chapters").size());
        assertEquals("第二卷 旅途篇", node.path("data").path("volumes").get(1).path("title").asText());
    }

    @Test
    void shouldUploadCoverAndReturnBytes() throws Exception {
        String token = newUserToken();
        Long bookId = createBook(token, "Cover Probe");
        byte[] pngBytes = new byte[]{1, 2, 3, 4};
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.png", "image/png", pngBytes);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/books/" + bookId + "/cover")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        MvcResult result = mockMvc.perform(get("/api/books/" + bookId + "/cover")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals("image/png", result.getResponse().getContentType());
        assertEquals(pngBytes.length, result.getResponse().getContentAsByteArray().length);
    }

    @Test
    void shouldRejectUnsupportedTextExtension() throws Exception {
        String token = newUserToken();
        Long bookId = createBook(token, "Bad Extension");
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.exe", "application/octet-stream", new byte[]{1, 2, 3});

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/books/" + bookId + "/text")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void shouldRejectCrossUserAttachmentUpload() throws Exception {
        String ownerToken = newUserToken();
        String otherToken = newUserToken();
        Long bookId = createBook(ownerToken, "Owned Attachment");
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.txt", "text/plain", "content".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/books/" + bookId + "/text")
                        .file(file)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @AfterEach
    void cleanup() {
        if (!cacheKeys.isEmpty()) {
            redisTemplate.delete(cacheKeys);
            cacheKeys.clear();
        }
        if (!createdBookIds.isEmpty()) {
            chapterMapper.delete(new QueryWrapper<org.example.bookmanager.entity.BookChapter>()
                    .in("book_id", createdBookIds));
            coverMapper.deleteBatchIds(createdBookIds);
            textMapper.deleteBatchIds(createdBookIds);
            bookMapper.deleteBatchIds(createdBookIds);
            createdBookIds.clear();
        }
        if (!createdUsernames.isEmpty()) {
            QueryWrapper<UserAccount> wrapper = new QueryWrapper<>();
            wrapper.in("username", createdUsernames);
            userAccountMapper.delete(wrapper);
            createdUsernames.clear();
        }
    }
}
