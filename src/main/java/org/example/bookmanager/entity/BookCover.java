package org.example.bookmanager.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("book_cover")
public class BookCover {

    @TableId(value = "book_id", type = IdType.INPUT)
    private Long bookId;

    private String fileName;

    private String mime;

    private Long size;

    private byte[] data;

    private LocalDateTime updatedAt;
}
