package com.yin.picture.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yin.picture.common.RedisKeyUtil;
import com.yin.picture.constant.RedisLuaScriptConstant;
import com.yin.picture.listener.thumb.msg.ThumbEvent;
import com.yin.picture.mapper.ThumbMapper;
import com.yin.picture.model.dto.thumb.DoThumbRequest;
import com.yin.picture.model.entity.Thumb;
import com.yin.picture.model.entity.User;
import com.yin.picture.model.enums.LuaStatusEnum;
import com.yin.picture.service.ThumbService;
import com.yin.picture.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service("thumbService")
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceMQImpl extends ServiceImpl<ThumbMapper, Thumb>
        implements ThumbService {
  
    private final UserService userService;
  
    private final StringRedisTemplate stringRedisTemplate;
  
    private final PulsarTemplate<ThumbEvent> pulsarTemplate;
  
    @SneakyThrows
    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getPictureId() == null) {
            throw new RuntimeException("参数错误");  
        }  
        User loginUser = userService.getLoginUser(request);
        Long loginUserId = loginUser.getId();  
        Long pictureId = doThumbRequest.getPictureId();
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUserId);
        // 执行 Lua 脚本，点赞存入 Redis  
        long result = stringRedisTemplate.execute(
                RedisLuaScriptConstant.THUMB_SCRIPT_MQ,
                List.of(userThumbKey),
                pictureId.toString()
        );  
        if (LuaStatusEnum.FAIL.getValue() == result) {
            throw new RuntimeException("用户已点赞");  
        }  
  
        ThumbEvent thumbEvent = ThumbEvent.builder()  
                .pictureId(pictureId)
                .userId(loginUserId)  
                .type(ThumbEvent.EventType.INCR)  
                .eventTime(LocalDateTime.now())
                .build();  
        pulsarTemplate.sendAsync("thumb-topic", thumbEvent).exceptionally(ex -> {  
            stringRedisTemplate.opsForHash().delete(userThumbKey, pictureId.toString(), true);
            log.error("点赞事件发送失败: userId={}, pictureId={}", loginUserId, pictureId, ex);
            return null;  
        });  
  
        return true;  
    }  
  
    @SneakyThrows
    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {  
        if (doThumbRequest == null || doThumbRequest.getPictureId() == null) {
            throw new RuntimeException("参数错误");  
        }  
        User loginUser = userService.getLoginUser(request);  
        Long loginUserId = loginUser.getId();  
        Long pictureId = doThumbRequest.getPictureId();
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUserId);  
        // 执行 Lua 脚本，点赞记录从 Redis 删除  
        long result = stringRedisTemplate.execute(
                RedisLuaScriptConstant.UNTHUMB_SCRIPT_MQ,  
                List.of(userThumbKey),  
                pictureId.toString()
        );  
        if (LuaStatusEnum.FAIL.getValue() == result) {  
            throw new RuntimeException("用户未点赞");  
        }  
        ThumbEvent thumbEvent = ThumbEvent.builder()  
                .pictureId(pictureId)
                .userId(loginUserId)  
                .type(ThumbEvent.EventType.DECR)  
                .eventTime(LocalDateTime.now())  
                .build();  
        pulsarTemplate.sendAsync("thumb-topic", thumbEvent).exceptionally(ex -> {  
            stringRedisTemplate.opsForHash().put(userThumbKey, pictureId.toString(), true);
            log.error("点赞事件发送失败: userId={}, pictureId={}", loginUserId, pictureId, ex);
            return null;  
        });  
  
        return true;  
    }  
  
    @Override  
    public Boolean hasThumb(Long blogId, Long userId) {  
        return stringRedisTemplate.opsForHash().hasKey(RedisKeyUtil.getUserThumbKey(userId), blogId.toString());
    }  
  
}
