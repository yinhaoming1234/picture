package com.yin.picture.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.yin.picture.exception.BusinessException;
import com.yin.picture.exception.ErrorCode;
import com.yin.picture.exception.ThrowUtils;
import com.yin.picture.mapper.SpaceMapper;
import com.yin.picture.model.dto.space.SpaceAddRequest;
import com.yin.picture.model.dto.space.SpaceQueryRequest;
import com.yin.picture.model.entity.Picture;
import com.yin.picture.model.entity.Space;
import com.yin.picture.model.entity.SpaceUser;
import com.yin.picture.model.entity.User;
import com.yin.picture.model.enums.SpaceLevelEnum;
import com.yin.picture.model.enums.SpaceRoleEnum;
import com.yin.picture.model.enums.SpaceTypeEnum;
import com.yin.picture.model.vo.SpaceVO;
import com.yin.picture.model.vo.UserVO;
import com.yin.picture.service.PictureService;
import com.yin.picture.service.SpaceService;
import com.yin.picture.service.SpaceUserService;
import com.yin.picture.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceService {

    private final UserService userService;
    private final TransactionTemplate transactionTemplate;
    private final RedissonClient redissonClient;
    private final PictureService pictureService;
    private final SpaceUserService spaceUserService;

    public void validSpace(Space space, boolean add) {
        Integer spaceType = space.getSpaceType();
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(spaceType);
        // 要创建
        if (add) {
            if (spaceType == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类型不能为空");
            }
        }
        // 修改数据时，如果要改空间级别
        if (spaceType != null && spaceTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类型不存在");
        }
    }


    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        // 根据空间级别，自动填充限额
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 对象转封装类
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 关联查询用户信息
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }
    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        // 对象列表 => 封装对象列表
        List<SpaceVO> spaceVOList = spaceList.stream()
                .map(SpaceVO::objToVo)
                .collect(Collectors.toList());
        // 1. 关联查询用户信息
        // 1,2,3,4
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        // 1 => user1, 2 => user2
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceVO.setUser(userService.getUserVO(user));
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        Integer spaceType = spaceQueryRequest.getSpaceType();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "spaceType", spaceType);
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }
    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
        // 仅本人或管理员可编辑
        if (!space.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }




    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {

// 默认值
        if (StrUtil.isBlank(spaceAddRequest.getSpaceName())) {
            spaceAddRequest.setSpaceName("默认空间");
        }
        if (spaceAddRequest.getSpaceLevel() == null) {
            spaceAddRequest.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        if (spaceAddRequest.getSpaceType() == null) {
            spaceAddRequest.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        // 在此处将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
// 填充数据
        this.fillSpaceBySpaceLevel(space);

        // 数据校验
        this.validSpace(space, true);
        Long userId = loginUser.getId();
        space.setUserId(userId);
        // 权限校验
        if (SpaceLevelEnum.COMMON.getValue() != spaceAddRequest.getSpaceLevel() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的空间");
        }
        // 针对用户进行加锁
        RLock lock = redissonClient.getLock(String.valueOf(userId).intern()); // 任何字符串都可以作为锁的key
        boolean isLocked = false;
        try {
            // 2. 尝试在10秒内获取锁，获取后看门狗机制生效
            isLocked = lock.tryLock(10, TimeUnit.SECONDS);

            if (isLocked) {
                Long newSpaceId = transactionTemplate.execute(status -> {
                    if (!userService.isAdmin(loginUser)) {
                        boolean exists = this.lambdaQuery()
                                .eq(Space::getUserId, userId)
                                .eq(Space::getSpaceType, spaceAddRequest.getSpaceType())
                                .exists();
                        ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户每类空间仅能创建一个");
                    }
                    // 写入数据库
                    boolean result = this.save(space);
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
// 如果是团队空间，关联新增团队成员记录
                    if (SpaceTypeEnum.TEAM.getValue() == spaceAddRequest.getSpaceType()) {
                        SpaceUser spaceUser = new SpaceUser();
                        spaceUser.setSpaceId(space.getId());
                        spaceUser.setUserId(userId);
                        spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                        result = spaceUserService.save(spaceUser);
                        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建团队成员记录失败");
                    }
// 返回新写入的数据 id
                    return space.getId();

                });


            } else {
                // 在指定时间内未获取到锁
                log.warn("创建空间操作超时，未能获取到锁");
                // 可以直接抛出异常，让上层处理，或者返回一个特定错误码
                throw new RuntimeException("系统繁忙");
            }

        } catch (InterruptedException e) {
            // tryLock 是可中断的，如果当前线程在等待锁的过程中被中断，会进入这里
            log.error("等待分布式锁时线程被中断", e);
            // 恢复中断状态
            Thread.currentThread().interrupt();
            throw new RuntimeException("线程被中断");
        } finally {
            // 4. 释放锁
            if (isLocked) { // 只有在成功加锁的情况下才需要解锁
                lock.unlock();
                System.out.println("线程 " + Thread.currentThread().getId() + " 释放了锁。");
            }
        }
        return 0;
    }

    @Override
    public void deleteSpace(long spaceId, User loginUser) {
        transactionTemplate.execute(
                status -> {
                    Space oldSpace = this.getById(spaceId);
                    ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);
                    // 仅本人或者管理员可删除
                    this.checkSpaceAuth(loginUser, oldSpace);
                    // 操作数据库
                    boolean result = this.removeById(spaceId);
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
                    QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
                    queryWrapper.eq("spaceId", spaceId);
                    boolean result2 = pictureService.remove(queryWrapper);
                    return true;
                }
        );

    }
}


