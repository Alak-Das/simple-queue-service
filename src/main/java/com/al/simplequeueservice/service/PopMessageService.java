package com.al.simplequeueservice.service;

import com.al.simplequeueservice.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.Executor;

import static com.al.simplequeueservice.util.SQSConstants.*;

@Service
public class PopMessageService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private CacheService cacheService;

    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    private static final Logger logger = LoggerFactory.getLogger(PopMessageService.class);

    public Optional<Message> pop(String consumerGroup) {
        logger.debug("Popping oldest message from the Queue for Consumer Group: {}", consumerGroup);
        // Get from Cache
        Message cachedMessage = cacheService.popMessage(consumerGroup);
        if (cachedMessage != null) {
            logger.debug("Message found in cache for Consumer Group: {}", consumerGroup);
            taskExecutor.execute(() -> {
                logger.debug("Asynchronously updating message as consumed=true for Consumer Group: {}", consumerGroup);
                updateMessageInMongo(cachedMessage.getId(), consumerGroup);
            });
            return Optional.of(cachedMessage);
        }
        // Get from DB if not in Cache
        logger.debug("Message not found in cache for Consumer Group: {}. Fetching from DB", consumerGroup);
        Query query = new Query(Criteria.where(CONSUMED).is(false))
                .with(Sort.by(Sort.Direction.ASC, CREATED_AT));
        Update update = new Update().set(CONSUMED, true);
        FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
        Message message = mongoTemplate.findAndModify(query, update, options, Message.class, consumerGroup);

        return Optional.ofNullable(message);
    }

    private void updateMessageInMongo(String messageId, String consumerGroup) {
        Query query = new Query(Criteria.where(ID).is(messageId));
        Message originalMessage = mongoTemplate.findOne(query, Message.class, consumerGroup);
        if (originalMessage != null) {
            Message updatedMessage = originalMessage.markConsumed();
            mongoTemplate.save(updatedMessage, consumerGroup);
            logger.debug("Message with ID: {} in Consumer Group: {} updated to consumed: {}", messageId, consumerGroup, true);
        } else {
            logger.warn("Message with ID: {} not found in Consumer Group: {} for update.", messageId, consumerGroup);
        }
    }
}
