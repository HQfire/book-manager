package org.example.bookmanager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("book")
public class Book {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String author;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
