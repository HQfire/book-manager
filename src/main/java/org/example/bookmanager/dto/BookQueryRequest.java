package org.example.bookmanager.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookQueryRequest {

    @NotNull(message = "userId is required")
    private Long userId;

    private String title;

    private String author;

    private long pageNum = 1;

    private long pageSize = 10;
}
