package org.example.bookmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BookSaveRequest {

    @NotBlank(message = "title is required")
    @Size(max = 100, message = "title must be at most 100 characters")
    private String title;

    @Size(max = 100, message = "author must be at most 100 characters")
    private String author;

    @Size(max = 500, message = "description must be at most 500 characters")
    private String description;
}
