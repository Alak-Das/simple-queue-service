package com.al.simplequeueservice.service;

import com.al.simplequeueservice.model.Message;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.al.simplequeueservice.util.SQSConstants.*;

@Service
public class ViewMessageService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private CacheService cacheService;

    private static final Logger logger = LoggerFactory.getLogger(ViewMessageService.class);

    public List<Message> view(String consumerGroup, int messageCount, String consumed) {
        logger.debug("Viewing all messages in the Queue for Consumer Group: {}. Filter by consumed: {}", consumerGroup, StringUtils.isEmpty(consumed) ? "" : consumed);

        List<Message> combinedMessages = new ArrayList<>();

        Query query = new Query();
        query.addCriteria(Criteria.where("consumerGroup").is(consumerGroup));

        if (consumed != null) {
            if (consumed.equalsIgnoreCase("yes")) {
                query.addCriteria(Criteria.where(CONSUMED).is(true));
            } else if (consumed.equalsIgnoreCase("no")) {
                // Get from Cache
                List<Message> cachedMessages = cacheService.viewMessages(consumerGroup).stream().limit(messageCount).toList();
                combinedMessages.addAll(cachedMessages);
                Set<String> cachedMessageIds = cachedMessages.stream()
                        .map(Message::getId)
                        .collect(Collectors.toSet());
                // Exclude messages already found in cache
                if (!cachedMessageIds.isEmpty()) {
                    if(cachedMessageIds.size() < messageCount){
                        query.addCriteria(Criteria.where(ID).nin(cachedMessageIds));
                    }else {
                        return combinedMessages;
                    }
                }
                query.addCriteria(Criteria.where(CONSUMED).is(false));
            }
        }

        query.limit(messageCount - combinedMessages.size());
        List<Message> mongoMessages = mongoTemplate.find(query, Message.class, consumerGroup);
        combinedMessages.addAll(mongoMessages);

        // Sort by createdAt to maintain consistent order
        combinedMessages.sort(Comparator.comparing(Message::getCreatedAt));

        logger.debug("Returning a combined list of {} unique messages for Consumer Group: {}", combinedMessages.size(), consumerGroup);
        return combinedMessages;
    }
}
