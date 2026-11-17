package org.example.bookmanager.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.bookmanager.common.PageResult;
import org.example.bookmanager.common.Result;
import org.example.bookmanager.config.AuthInterceptor;
import org.example.bookmanager.dto.BookQueryRequest;
import org.example.bookmanager.dto.BookSaveRequest;
import org.example.bookmanager.dto.ReaderDTO;
import org.example.bookmanager.entity.Book;
import org.example.bookmanager.entity.BookCover;
import org.example.bookmanager.service.BookService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @PostMapping
    public Result<Book> create(@Valid @RequestBody BookSaveRequest request, HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        return Result.ok(bookService.create(request, currentUserId));
    }

    @PutMapping("/{id}")
    public Result<Book> update(@PathVariable Long id, @Valid @RequestBody BookSaveRequest request,
                               HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        return Result.ok(bookService.update(id, request, currentUserId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        bookService.delete(id, currentUserId);
        return Result.ok(null);
    }

    @GetMapping("/{id}")
    public Result<Book> getById(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        return Result.ok(bookService.getById(id, currentUserId));
    }

    @GetMapping("/page")
    public Result<PageResult<Book>> page(BookQueryRequest query, HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        return Result.ok(bookService.page(query, currentUserId));
    }

    @PostMapping("/{id}/text")
    public Result<Void> uploadText(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                    HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        bookService.attachText(id, currentUserId, file);
        return Result.ok(null);
    }

    @PostMapping("/{id}/cover")
    public Result<Void> uploadCover(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                    HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        bookService.attachCover(id, currentUserId, file);
        return Result.ok(null);
    }

    @GetMapping("/{id}/reader")
    public Result<ReaderDTO> reader(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        return Result.ok(bookService.getReader(id, currentUserId));
    }

    @GetMapping("/{id}/cover")
    public ResponseEntity<byte[]> cover(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long currentUserId = currentUserId(httpRequest);
        BookCover cover = bookService.getCover(id, currentUserId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, cover.getMime())
                .body(cover.getData());
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(AuthInterceptor.ATTR_USER_ID);
    }
}
