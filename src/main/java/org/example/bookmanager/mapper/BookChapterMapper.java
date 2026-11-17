package org.example.bookmanager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.bookmanager.entity.BookChapter;

@Mapper
public interface BookChapterMapper extends BaseMapper<BookChapter> {
}
