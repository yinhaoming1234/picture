package com.yin.picture.job;

import com.yin.picture.constant.ThumbConstant;
import com.yin.picture.listener.thumb.msg.ThumbEvent;
import com.yin.picture.model.entity.Thumb;
import com.yin.picture.service.ThumbService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.shade.com.google.common.collect.Sets;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ThumbReconcileJob {  
    @Resource
    private StringRedisTemplate stringRedisTemplate;
  
    @Resource  
    private ThumbService thumbService;
  
    @Resource  
    private PulsarTemplate<ThumbEvent> pulsarTemplate;
  
    /**  
     * 定时任务入口（每天凌晨2点执行）  
     */  
    @Scheduled(cron = "0 0 2 * * ?")
    public void run() {  
        long startTime = System.currentTimeMillis();  
  
        // 1. 获取该分片下的所有用户ID  
        Set<Long> userIds = new HashSet<>();
        String pattern = ThumbConstant.USER_THUMB_KEY_PREFIX + "*";
        try (Cursor<String> cursor = stringRedisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
            while (cursor.hasNext()) {  
                String key = cursor.next();  
                Long userId = Long.valueOf(key.replace(ThumbConstant.USER_THUMB_KEY_PREFIX, ""));  
                userIds.add(userId);  
            }  
        }
        // 2. 逐用户比对  
        userIds.forEach(userId -> {  
            Set<Long> redisBlogIds = stringRedisTemplate.opsForHash().keys(ThumbConstant.USER_THUMB_KEY_PREFIX + userId).stream().map(obj -> Long.valueOf(obj.toString())).collect(Collectors.toSet());
            Set<Long> mysqlBlogIds = Optional.ofNullable(thumbService.lambdaQuery()
                            .eq(Thumb::getUserId, userId)
                            .list()  
                    ).orElse(new ArrayList<>())
                    .stream()  
                    .map(Thumb::getPictureId)
                    .collect(Collectors.toSet());  
  
            // 3. 计算差异（Redis有但MySQL无）  
            Set<Long> diffBlogIds = Sets.difference(redisBlogIds, mysqlBlogIds);
  
            // 4. 发送补偿事件  
            sendCompensationEvents(userId, diffBlogIds);  
        });  
  
        log.info("对账任务完成，耗时 {}ms", System.currentTimeMillis() - startTime);  
    }  
  
    /**  
     * 发送补偿事件到Pulsar  
     */  
    private void sendCompensationEvents(Long userId, Set<Long> blogIds) {  
        blogIds.forEach(blogId -> {
            ThumbEvent thumbEvent = new ThumbEvent(userId, blogId, ThumbEvent.EventType.INCR, LocalDateTime.now());
            try {
                pulsarTemplate.sendAsync("thumb-topic", thumbEvent)
                        .exceptionally(ex -> {
                            log.error("补偿事件发送失败: userId={}, blogId={}", userId, blogId, ex);
                            return null;
                        });
            } catch (PulsarClientException e) {
                log.error("发送补偿事件失败: userId={}, blogId={}", userId, blogId, e);
            }
        });  
    }  
}
