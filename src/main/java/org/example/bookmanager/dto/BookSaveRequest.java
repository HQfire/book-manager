package org.example.bookmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BookSaveRequest {

    @NotNull(message = "userId is required")
    private Long userId;

    @NotBlank(message = "title is required")
    @Size(max = 100, message = "title must be at most 100 chars")
    private String title;

    @Size(max = 100, message = "author must be at most 100 chars")
    private String author;

    @Size(max = 500, message = "description must be at most 500 chars")
    private String description;
}
