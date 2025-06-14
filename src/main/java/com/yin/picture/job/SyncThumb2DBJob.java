package com.yin.picture.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.StrPool;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yin.picture.common.RedisKeyUtil;
import com.yin.picture.mapper.PictureMapper;
import com.yin.picture.model.entity.Thumb;
import com.yin.picture.model.enums.ThumbTypeEnum;
import com.yin.picture.service.ThumbService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 定时将 Redis 中的临时点赞数据同步到数据库  
 *  
 */  
@Component
@Slf4j
@Async
public class SyncThumb2DBJob {  
  
    @Resource
    private ThumbService thumbService;
  
    @Resource  
    private PictureMapper pictureMapper;
  
    @Resource  
    private StringRedisTemplate stringRedisTemplate;
  
    @Scheduled(fixedRate = 10000)
    @Transactional(rollbackFor = Exception.class)
    public void run() {  
        log.info("开始执行");  
        DateTime nowDate = DateUtil.date();
        // 如果秒数为0~9 则回到上一分钟的50秒
        int second = (DateUtil.second(nowDate) / 10 - 1) * 10;
        if (second == -10) {
            second = 50;
            // 回到上一分钟
            nowDate = DateUtil.offsetMinute(nowDate, -1);
        }
        String date = DateUtil.format(nowDate, "HH:mm:") + second;
        syncThumb2DBByDate(date);
        log.info("临时数据同步完成");
    }  
  
    public void syncThumb2DBByDate(String date) {  
        // 获取到临时点赞和取消点赞数据  
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(date);
        Map<Object, Object> allTempThumbMap = stringRedisTemplate.opsForHash().entries(tempThumbKey);
        boolean thumbMapEmpty = CollUtil.isEmpty(allTempThumbMap);
  
        // 同步 点赞 到数据库  
        // 构建插入列表并收集blogId  
        Map<Long, Long> pictureThumbCountMap = new HashMap<>();
        if (thumbMapEmpty) {  
            return;  
        }  
        ArrayList<Thumb> thumbList = new ArrayList<>();
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<>();
        boolean needRemove = false;  
        for (Object userIdPictureIdObj : allTempThumbMap.keySet()) {
            String userIdPictureIdObj1 = (String) userIdPictureIdObj;
            String[] userIdAndPictureId = userIdPictureIdObj1.split(StrPool.COLON);
            Long userId = Long.valueOf(userIdAndPictureId[0]);
            Long pictureId = Long.valueOf(userIdAndPictureId[1]);
            // -1 取消点赞，1 点赞  
            Integer thumbType = Integer.valueOf(allTempThumbMap.get(userIdPictureIdObj1).toString());
            if (thumbType == ThumbTypeEnum.INCR.getValue()) {
                Thumb thumb = new Thumb();  
                thumb.setUserId(userId);  
                thumb.setPictureId(pictureId);
                thumbList.add(thumb);  
            } else if (thumbType == ThumbTypeEnum.DECR.getValue()) {  
                // 拼接查询条件，批量删除  
                needRemove = true;  
                wrapper.or().eq(Thumb::getUserId, userId).eq(Thumb::getPictureId, pictureId);
            } else {  
                if (thumbType != ThumbTypeEnum.NON.getValue()) {  
                    log.warn("数据异常：{}", userId + "," + pictureId + "," + thumbType);
                }  
                continue;  
            }  
            // 计算点赞增量  
            pictureThumbCountMap.put(pictureId, pictureThumbCountMap.getOrDefault(pictureId, 0L) + thumbType);
        }  
        // 批量插入  
        thumbService.saveBatch(thumbList);  
        // 批量删除  
        if (needRemove) {  
            thumbService.remove(wrapper);  
        }  
        // 批量更新博客点赞量  
        if (!pictureThumbCountMap.isEmpty()) {
            pictureMapper.batchUpdateThumbCount(pictureThumbCountMap);
        }  

        stringRedisTemplate.delete(tempThumbKey);
    }  
}
