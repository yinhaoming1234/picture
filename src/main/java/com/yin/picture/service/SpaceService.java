package com.yin.picture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yin.picture.model.dto.space.SpaceAddRequest;
import com.yin.picture.model.dto.space.SpaceQueryRequest;
import com.yin.picture.model.entity.Space;
import com.yin.picture.model.entity.User;
import com.yin.picture.model.vo.SpaceVO;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author xiaoh
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2025-06-17 16:22:55
*/
public interface SpaceService extends IService<Space> {

    void validSpace(Space space, boolean add);

    void fillSpaceBySpaceLevel(Space space);

    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    void checkSpaceAuth(User loginUser, Space space);

    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    void deleteSpace(long spaceId, User loginUser);
}
