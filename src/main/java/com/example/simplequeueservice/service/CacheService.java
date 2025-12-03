package com.example.simplequeueservice.service;

import com.example.simplequeueservice.model.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CacheService {

    public static final String CACHE_PREFIX = "consumerGroupMessages:";

    private final RedisTemplate<String, Object> redisTemplate;

    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void addMessage(Message message) {
        String key = CACHE_PREFIX + message.getConsumerGroup();
        redisTemplate.opsForList().leftPush(key, message);
    }

    public Message popMessage(String consumerGroup) {
        String key = CACHE_PREFIX + consumerGroup;
        return (Message) redisTemplate.opsForList().rightPop(key);
    }
}
