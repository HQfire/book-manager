package org.example.bookmanager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.bookmanager.dto.BookQueryRequest;
import org.example.bookmanager.entity.Book;

import java.util.List;

@Mapper
public interface BookMapper extends BaseMapper<Book> {

    @Select("<script>"
            + "SELECT * FROM book WHERE user_id = #{q.userId} "
            + "<if test='q.title != null and q.title != \"\"'> AND title LIKE CONCAT('%', #{q.title}, '%') </if>"
            + "<if test='q.author != null and q.author != \"\"'> AND author LIKE CONCAT('%', #{q.author}, '%') </if>"
            + " ORDER BY id DESC LIMIT #{offset}, #{size}"
            + "</script>")
    List<Book> selectPage(@Param("q") BookQueryRequest query,
                          @Param("offset") long offset,
                          @Param("size") long size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM book WHERE user_id = #{q.userId} "
            + "<if test='q.title != null and q.title != \"\"'> AND title LIKE CONCAT('%', #{q.title}, '%') </if>"
            + "<if test='q.author != null and q.author != \"\"'> AND author LIKE CONCAT('%', #{q.author}, '%') </if>"
            + "</script>")
    long countByQuery(@Param("q") BookQueryRequest query);
}
