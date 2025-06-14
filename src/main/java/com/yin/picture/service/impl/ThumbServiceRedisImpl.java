package com.yin.picture.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yin.picture.common.RedisKeyUtil;
import com.yin.picture.constant.RedisLuaScriptConstant;
import com.yin.picture.constant.ThumbConstant;
import com.yin.picture.mapper.ThumbMapper;
import com.yin.picture.model.dto.thumb.DoThumbRequest;
import com.yin.picture.model.entity.Thumb;
import com.yin.picture.model.entity.User;
import com.yin.picture.model.enums.LuaStatusEnum;
import com.yin.picture.service.ThumbService;
import com.yin.picture.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service("thumbService")
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceRedisImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService {
  
    private final UserService userService;
  
    private final StringRedisTemplate stringRedisTemplate;
  
    @Override  
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getPictureId() == null) {
            throw new RuntimeException("参数错误");  
        }  
        User loginUser = userService.getLoginUser(request);
        Long pictureId = doThumbRequest.getPictureId();
  
        String timeSlice = getTimeSlice();  
        // Redis Key  
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());
        String pictureExistKey = ThumbConstant.PICTURE_EXIST_KEY_PREFIX;
  
        // 执行 Lua 脚本
        long result = stringRedisTemplate.execute(
                RedisLuaScriptConstant.THUMB_SCRIPT,
                Arrays.asList(pictureExistKey,tempThumbKey, userThumbKey),
                loginUser.getId().toString(),
                pictureId.toString()
        );  
  
        if (LuaStatusEnum.FAIL.getValue() == result) {
            throw new RuntimeException("用户已点赞");  
        }
        if (LuaStatusEnum.PICTURE_NOT_EXISTS.getValue() == result) {
            throw new RuntimeException("图片不存在");
        }
  
        // 更新成功才执行  
        return LuaStatusEnum.SUCCESS.getValue() == result;  
    }  
  
    @Override  
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {  
        if (doThumbRequest == null || doThumbRequest.getPictureId() == null) {
            throw new RuntimeException("参数错误");  
        }  
        User loginUser = userService.getLoginUser(request);  
  
        Long pictureId = doThumbRequest.getPictureId();
        // 计算时间片  
        String timeSlice = getTimeSlice();  
        // Redis Key  
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);  
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());
  
        // 执行 Lua 脚本  
        long result = stringRedisTemplate.execute(
                RedisLuaScriptConstant.UNTHUMB_SCRIPT,  
                Arrays.asList(tempThumbKey, userThumbKey),
                loginUser.getId().toString(),
                pictureId.toString()
        );  
        // 根据返回值处理结果  
        if (result == LuaStatusEnum.FAIL.getValue()) {  
            throw new RuntimeException("用户未点赞");  
        }  
        return LuaStatusEnum.SUCCESS.getValue() == result;  
    }  
  
    private String getTimeSlice() {  
        DateTime nowDate = DateUtil.date();
        // 获取到当前时间前最近的整数秒，比如当前 11:20:23 ，获取到 11:20:20  
        return DateUtil.format(nowDate, "HH:mm:") + (DateUtil.second(nowDate) / 10) * 10;  
    }  
  
    @Override  
    public Boolean hasThumb(Long pictureId, Long userId) {
        return stringRedisTemplate.opsForHash().hasKey(RedisKeyUtil.getUserThumbKey(userId), pictureId.toString());
    }  
}
