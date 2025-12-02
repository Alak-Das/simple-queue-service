package com.example.simplequeueservice.config;
import com.example.simplequeueservice.model.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Configuration
public class MongoTTLConfig {

    private final MongoTemplate mongoTemplate;

    @Value("${message.expiry.minutes}")
    private int expiryMinutes;

    public MongoTTLConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void createTTLIndex() {
        mongoTemplate.indexOps(Message.class)
                .ensureIndex(new Index()
                        .on("createdAt", Sort.Direction.ASC)
                        .expire(expiryMinutes, TimeUnit.MINUTES));
    }
}
