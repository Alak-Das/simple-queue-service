package com.example.simplequeueservice.service;

import com.example.simplequeueservice.model.Message;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class MessageService {

    @Autowired
    MongoClient mongoClient;
    @Value("${spring.data.mongodb.database}")
    private String mongoDB;
    @Value("${persistence.duration.minutes}")
    private long expireMinutes;
    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private CacheService cacheService;
    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    private static final String indexField = "createdAt";

    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

    public Message push(Message message) {
        // Save the Message to Cache
        cacheService.addMessage(message);

        // Save the Message to Persistence Storage
        createTTLIndex(message);
        logger.info("Saving message with content: {} to Consumer Group: {}", message.getContent(), message.getConsumerGroup());

        return mongoTemplate.save(message, message.getConsumerGroup());
    }

    public Optional<Message> pop(String consumerGroup) {
        logger.info("Popping oldest message from the Queue for Consumer Group: {}", consumerGroup);

        Message cachedMessage = cacheService.popMessage(consumerGroup);
        if (cachedMessage != null) {
            logger.info("Message found in cache for Consumer Group: {}", consumerGroup);
            taskExecutor.execute(() -> {
                logger.info("Asynchronously updating message as consumed=true for Consumer Group: {}", consumerGroup);
                updateMessageInMongo(cachedMessage.getId(), consumerGroup, true);
            });
            return Optional.of(cachedMessage);
        }

        logger.info("Message not found in cache for Consumer Group: {}. Fetching from DB", consumerGroup);
        Query query = new Query(Criteria.where("consumed").is(false))
                .with(Sort.by(Sort.Direction.ASC, "createdAt"));
        Update update = new Update().set("consumed", true);
        FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);

        Message message = mongoTemplate.findAndModify(query, update, options, Message.class, consumerGroup);

        return Optional.ofNullable(message);
    }

    public List<Message> view(String consumerGroup, String consumed) {
        logger.info("Viewing all messages in the Queue for Consumer Group: {}. Filter by consumed: {}", consumerGroup, StringUtils.isEmpty(consumed) ? "" : consumed);

        List<Message> combinedMessages = new ArrayList<>();

        Query query = new Query();
        query.addCriteria(Criteria.where("consumerGroup").is(consumerGroup));

        if (consumed != null) {
            if (consumed.equalsIgnoreCase("yes")) {
                query.addCriteria(Criteria.where("consumed").is(true));
            } else if (consumed.equalsIgnoreCase("no")) {
                // Get from Cache
                List<Message> cachedMessages = cacheService.viewMessages(consumerGroup);
                combinedMessages.addAll(cachedMessages);
                Set<String> cachedMessageIds = cachedMessages.stream()
                        .map(Message::getId)
                        .collect(Collectors.toSet());
                // Exclude messages already found in cache
                if (!cachedMessageIds.isEmpty()) {
                    query.addCriteria(Criteria.where("id").nin(cachedMessageIds));
                }
                query.addCriteria(Criteria.where("consumed").is(false));
            }
        }

        List<Message> mongoMessages = mongoTemplate.find(query, Message.class, consumerGroup);
        combinedMessages.addAll(mongoMessages);

        // Sort by createdAt to maintain consistent order
        combinedMessages.sort(Comparator.comparing(Message::getCreatedAt));

        logger.info("Returning a combined list of {} unique messages for Consumer Group: {}", combinedMessages.size(), consumerGroup);
        return combinedMessages;
    }

    private void updateMessageInMongo(String messageId, String consumerGroup, boolean consumed) {
        Query query = new Query(Criteria.where("id").is(messageId));
        Message originalMessage = mongoTemplate.findOne(query, Message.class, consumerGroup);
        if (originalMessage != null) {
            Message updatedMessage = originalMessage.markConsumed();
            mongoTemplate.save(updatedMessage, consumerGroup);
            logger.info("Message with ID: {} in Consumer Group: {} updated to consumed: {}", messageId, consumerGroup, consumed);
        } else {
            logger.warn("Message with ID: {} not found in Consumer Group: {} for update.", messageId, consumerGroup);
        }
    }

    private void createTTLIndex(Message message) {
        MongoCollection<Document> collection = mongoClient.getDatabase(mongoDB).getCollection(message.getConsumerGroup());
        boolean ttlExists = collection.listIndexes()
                .into(new ArrayList<>())
                .stream()
                .anyMatch(index -> {
                    Document key = (Document) index.get("key");
                    return key != null && key.containsKey(indexField);
                });

        if (!ttlExists) {
            logger.info("TTL index does not exist on field: {} for collection: {}. Creating...", indexField, message.getConsumerGroup());
            IndexOptions indexOptions = new IndexOptions().expireAfter(expireMinutes, TimeUnit.MINUTES);
            collection.createIndex(new Document(indexField, 1), indexOptions);
            logger.info("TTL index created on field: {} for collection: {}", indexField, message.getConsumerGroup());
        } else {
            logger.info("TTL index already exists on field: {} for collection: {}", indexField, message.getConsumerGroup());
        }
    }
}
