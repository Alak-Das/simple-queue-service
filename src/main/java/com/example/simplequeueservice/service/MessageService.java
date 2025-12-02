package com.example.simplequeueservice.service;

import com.example.simplequeueservice.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    public Message push(Message message) {
        logger.info("Saving message with content: {} to Consumer Group: {}", message.getContent(), message.getConsumerGroup());
        return mongoTemplate.save(message, message.getConsumerGroup());
    }

    public Optional<Message> pop(String consumerGroup) {
        logger.info("Popping oldest message from the Queue for Consumer Group: {}", consumerGroup);

        Query query = new Query(Criteria.where("processed").is(false))
                .with(Sort.by(Sort.Direction.ASC, "createdAt"));
        Update update = new Update().set("processed", true);
        FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);

        Message message = mongoTemplate.findAndModify(query, update, options, Message.class, consumerGroup);

        return Optional.ofNullable(message);
    }

    public List<Message> view(String consumerGroup, String processed) {
        logger.info("Viewing all messages in the Queue for Consumer Group: {}. Filter by processed: {}", consumerGroup, processed);
        Query query = new Query();
        query.addCriteria(Criteria.where("consumerGroup").is(consumerGroup));

        if (processed != null) {
            if (processed.equalsIgnoreCase("yes")) {
                query.addCriteria(Criteria.where("processed").is(true));
            } else if (processed.equalsIgnoreCase("no")) {
                query.addCriteria(Criteria.where("processed").is(false));
            }
        }
        return mongoTemplate.find(query, Message.class, consumerGroup);
    }
}
