package org.example.bookmanager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.bookmanager.entity.BookCover;

@Mapper
public interface BookCoverMapper extends BaseMapper<BookCover> {
}
