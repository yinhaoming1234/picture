package com.yin.picture.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yin.picture.cache.CacheManager;
import com.yin.picture.constant.ThumbConstant;
import com.yin.picture.mapper.ThumbMapper;
import com.yin.picture.model.dto.thumb.DoThumbRequest;
import com.yin.picture.model.entity.Picture;
import com.yin.picture.model.entity.Thumb;
import com.yin.picture.model.entity.User;
import com.yin.picture.service.PictureService;
import com.yin.picture.service.ThumbService;
import com.yin.picture.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Year;
import java.util.concurrent.TimeUnit;

/**
* @author hao
* @description 针对表【thumb】的数据库操作Service实现
* @createDate 2025-06-09 21:00:15
*/
@Service("ThumbServiceDB")
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService {

    private final UserService userService;

    private final TransactionTemplate transactionTemplate;
    private final PictureService pictureService;
    private final RedissonClient redissonClient;
    private final CacheManager cacheManager;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getPictureId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        RLock lock = redissonClient.getLock("Thumb"+loginUser.getId().toString());
        try {
            boolean isLocked = lock.tryLock(10, 30, TimeUnit.SECONDS);

            if (isLocked) {
                // 编程式事务
                return transactionTemplate.execute(status -> {
                    Long pictureId = doThumbRequest.getPictureId();
                    boolean exists = hasThumb(pictureId, loginUser.getId());
                    if (exists) {
                        throw new RuntimeException("用户已点赞");
                    }

                    boolean update = pictureService.lambdaUpdate()
                            .eq(Picture::getId, pictureId)
                            .setSql("thumbCount = thumbCount + 1")
                            .update();

                    Thumb thumb = new Thumb();
                    thumb.setUserId(loginUser.getId());
                    thumb.setPictureId(pictureId);
                    // 更新成功才执行
                    Boolean success = update && this.save(thumb);
                    // 点赞记录存入 Redis
                    if (success) {
                        String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId();
                        String fieldKey = pictureId.toString();
                        Long realThumbId = thumb.getId();
                        stringRedisTemplate.opsForHash().put(hashKey, fieldKey, realThumbId);
                        cacheManager.putIfPresent(hashKey, fieldKey, realThumbId);
                    }
                    return success;

                });
            } else {
                log.warn("用户 {} 点赞操作获取锁失败，可能操作频繁", loginUser.getId());
                return false;

            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            // 确保锁一定被释放
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
                System.out.println("线程 " + Thread.currentThread().getId() + " 释放锁！");
            }
        }
    }


    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getPictureId() == null) {
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        RLock lock = redissonClient.getLock("Thumb"+loginUser.getId().toString());
        try {
            boolean isLocked = lock.tryLock(10, 30, TimeUnit.SECONDS);

            if (isLocked) {

            // 编程式事务
            return transactionTemplate.execute(status -> {
                Long pictureId = doThumbRequest.getPictureId();
                Object thumbIdObj = cacheManager.get(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId(), pictureId.toString());
                if (thumbIdObj == null || thumbIdObj.equals(ThumbConstant.UN_THUMB_CONSTANT)) {
                    throw new RuntimeException("用户未点赞");
                }

                boolean update = pictureService.lambdaUpdate()
                        .eq(Picture::getId, pictureId)
                        .setSql("thumbCount = thumbCount - 1")
                        .update();

                Boolean success = update && this.removeById((Long) thumbIdObj);
                // 点赞记录从 Redis 删除
                if (success) {
                    String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId();
                    String fieldKey = pictureId.toString();
                    stringRedisTemplate.opsForHash().delete(hashKey, fieldKey);
                    cacheManager.putIfPresent(hashKey, fieldKey, ThumbConstant.UN_THUMB_CONSTANT);
                }
                return success;

            });
            }else {
                log.warn("用户 {} 点赞操作获取锁失败，可能操作频繁", loginUser.getId());
                return false;
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            // 确保锁一定被释放
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
                System.out.println("线程 " + Thread.currentThread().getId() + " 释放锁！");
            }
        }
    }


    @Override
    public Boolean hasThumb(Long pictureId, Long userId) {
        Object thumbIdObj = cacheManager.get(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, pictureId.toString());
        if (thumbIdObj == null) {
            return false;
        }
        Long thumbId = (Long) thumbIdObj;
        return !thumbId.equals(ThumbConstant.UN_THUMB_CONSTANT);
    }



}





