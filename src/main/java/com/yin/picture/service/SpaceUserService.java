package com.yin.picture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yin.picture.model.dto.spaceuser.SpaceUserAddRequest;
import com.yin.picture.model.dto.spaceuser.SpaceUserQueryRequest;
import com.yin.picture.model.entity.SpaceUser;
import com.yin.picture.model.vo.SpaceUserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public interface SpaceUserService extends IService<SpaceUser> {
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    void validSpaceUser(SpaceUser spaceUser, boolean add);

    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);
}
