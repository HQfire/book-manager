package org.example.bookmanager.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.bookmanager.entity.Book;
import org.example.bookmanager.entity.UserAccount;
import org.example.bookmanager.mapper.BookMapper;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private String login(String username, String password) throws Exception {
        String body = OBJECT_MAPPER.writeValueAsString(Map.of("username", username, "password", password));
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode node = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        return node.path("data").path("token").asText();
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

    @AfterEach
    void cleanup() {
        if (!cacheKeys.isEmpty()) {
            redisTemplate.delete(cacheKeys);
            cacheKeys.clear();
        }
        if (!createdBookIds.isEmpty()) {
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
