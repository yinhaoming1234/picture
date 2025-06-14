package com.yin.picture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yin.picture.model.entity.Picture;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
* @author hao
* @description 针对表【picture(图片)】的数据库操作Mapper
* @createDate 2025-06-07 16:12:23
* @Entity com.yin.picture.model.entity.Picture
*/
public interface PictureMapper extends BaseMapper<Picture> {
    void batchUpdateThumbCount(@Param("countMap") Map<Long, Long> countMap);

}




