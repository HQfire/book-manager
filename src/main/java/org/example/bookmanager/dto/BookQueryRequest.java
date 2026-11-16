package org.example.bookmanager.dto;

import lombok.Data;

@Data
public class BookQueryRequest {

    private String title;

    private String author;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
