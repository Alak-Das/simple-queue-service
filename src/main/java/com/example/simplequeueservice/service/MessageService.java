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

@Service
public class MessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    public Message push(String consumerGroup, String content) {
        logger.info("Saving message with content: {} to consumer group: {}", content, consumerGroup);
        Message message = new Message(content);
        return mongoTemplate.save(message, consumerGroup);
    }

    public Optional<Message> pop(String consumerGroup) {
        logger.info("Popping oldest message from the queue for consumer group: {}", consumerGroup);

        Query query = new Query(Criteria.where("processed").is(false))
                .with(Sort.by(Sort.Direction.ASC, "createdAt"));
        Update update = new Update().set("processed", true);
        FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);

        Message message = mongoTemplate.findAndModify(query, update, options, Message.class, consumerGroup);

        return Optional.ofNullable(message);
    }

    public List<Message> view(String consumerGroup) {
        logger.info("Viewing all messages in the queue for consumer group: {}", consumerGroup);
        return mongoTemplate.findAll(Message.class, consumerGroup);
    }
}
