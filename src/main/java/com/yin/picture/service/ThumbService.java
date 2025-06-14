package com.yin.picture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yin.picture.model.dto.thumb.DoThumbRequest;
import com.yin.picture.model.entity.Thumb;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author hao
* @description 针对表【thumb】的数据库操作Service
* @createDate 2025-06-09 21:00:15
*/
public interface ThumbService extends IService<Thumb> {
    /**
     * 点赞
     * @param doThumbRequest
     * @param request
     * @return {@link Boolean }
     */
    Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);
    /**
     * 取消点赞
     * @param doThumbRequest
     * @param request
     * @return {@link Boolean }
     */
    Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);
    /**
     * 判断用户是否已经点赞
     * @param pictureId
     * @param userId
     * @return {@link Boolean }
     */
    Boolean hasThumb(Long pictureId, Long userId);


}
