package com.yin.picture.comsumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yin.picture.listener.thumb.msg.ThumbEvent;
import com.yin.picture.mapper.PictureMapper;
import com.yin.picture.model.entity.Thumb;
import com.yin.picture.service.ThumbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.common.schema.SchemaType;
import org.apache.pulsar.shade.org.apache.commons.lang3.tuple.Pair;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThumbConsumer {  

    private final PictureMapper pictureMapper;
    private final ThumbService thumbService;

    // 批量处理消息
    @PulsarListener(
            subscriptionName = "thumb-subscription",  
            topics = "thumb-topic",  
            schemaType = SchemaType.JSON,
            batch = true,  
            consumerCustomizer = "thumbConsumerConfig",
            // 引用 NACK 重试策略
            negativeAckRedeliveryBackoff = "negativeAckRedeliveryBackoff",
            // 引用 ACK 超时重试策略
            ackTimeoutRedeliveryBackoff = "ackTimeoutRedeliveryBackoff",
            // 死信仅适用于 shared 类型
            subscriptionType = SubscriptionType.Shared,
            // 引用死信队列策略
            deadLetterPolicy = "deadLetterPolicy"
    )  
    @Transactional(rollbackFor = Exception.class)
    public void processBatch(List<Message<ThumbEvent>> messages) {
        log.info("ThumbConsumer processBatch: {}", messages.size());  
        Map<Long, Long> countMap = new ConcurrentHashMap<>();
        List<Thumb> thumbs = new ArrayList<>();
  
        // 并行处理消息  
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<>();
        AtomicReference<Boolean> needRemove = new AtomicReference<>(false);
  
        // 提取事件并过滤无效消息  
        List<ThumbEvent> events = messages.stream()  
                .map(Message::getValue)  
                .filter(Objects::nonNull)
                .toList();  
  
  
        // 按(userId, blogId)分组，并获取每个分组的最新事件  
        Map<Pair<Long, Long>, ThumbEvent> latestEvents = events.stream()
                .collect(Collectors.groupingBy(
                        e -> Pair.of(e.getUserId(), e.getPictureId()),
                        Collectors.collectingAndThen(  
                                Collectors.toList(),  
                                list -> {  
                                    // 按时间升序排序，取最后一个作为最新事件  
                                    list.sort(Comparator.comparing(ThumbEvent::getEventTime));
                                    if (list.size() % 2 == 0) {  
                                        return null;  
                                    }  
                                    return list.get(list.size() - 1);  
                                }  
                        )  
                ));  
  
        latestEvents.forEach((userBlogPair, event) -> {  
            if (event == null) {  
                return;  
            }  
            ThumbEvent.EventType finalAction = event.getType();  
  
            if (finalAction == ThumbEvent.EventType.INCR) {  
                countMap.merge(event.getPictureId(), 1L, Long::sum);
                Thumb thumb = new Thumb();  
                thumb.setPictureId(event.getPictureId());
                thumb.setUserId(event.getUserId());  
                thumbs.add(thumb);  
            } else {  
                needRemove.set(true);  
                wrapper.or().eq(Thumb::getUserId, event.getUserId()).eq(Thumb::getPictureId ,event.getPictureId());
                countMap.merge(event.getPictureId(), -1L, Long::sum);
            }  
        });  
  
        // 批量更新数据库  
        if (Boolean.TRUE.equals(needRemove.get())) {
            thumbService.remove(wrapper);  
        }  
        batchUpdateBlogs(countMap);  
        batchInsertThumbs(thumbs);  
    }  
  
    public void batchUpdateBlogs(Map<Long, Long> countMap) {  
        if (!countMap.isEmpty()) {  
            pictureMapper.batchUpdateThumbCount(countMap);
        }  
    }  
  
    public void batchInsertThumbs(List<Thumb> thumbs) {  
        if (!thumbs.isEmpty()) {  
            // 分批次插入  
            thumbService.saveBatch(thumbs, 500);  
        }  
    }
    @PulsarListener(topics = "thumb-dlq-topic")
    public void consumerDlq(Message<ThumbEvent> message) {
        MessageId messageId = message.getMessageId();
        log.info("dlq message = {}", messageId);
        log.info("消息 {} 已入库", messageId);
    }

}
