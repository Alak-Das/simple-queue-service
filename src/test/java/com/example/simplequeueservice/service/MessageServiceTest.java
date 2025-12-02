package com.example.simplequeueservice.service;

import com.example.simplequeueservice.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class MessageServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void push() {
        String consumerGroup = "testGroup";
        String content = "testContent";
        Message messageToPush = new Message("testId", consumerGroup, content);
        when(mongoTemplate.save(any(Message.class), eq(consumerGroup))).thenReturn(messageToPush);

        Message result = messageService.push(messageToPush);
        assertNotNull(result);
        assertEquals(content, result.getContent());
        assertEquals(consumerGroup, result.getConsumerGroup());
    }

    @Test
    void pop() {
        String consumerGroup = "testGroup";
        Message message = new Message("testId", consumerGroup, "testContent");
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Message.class), eq(consumerGroup))).thenReturn(message);

        Optional<Message> result = messageService.pop(consumerGroup);
        assertTrue(result.isPresent());
        assertEquals(message.getContent(), result.get().getContent());
        assertEquals(message.getConsumerGroup(), result.get().getConsumerGroup());
    }

    @Test
    void view() {
        String consumerGroup = "testGroup";
        Message message = new Message("testId", consumerGroup, "testContent");
        List<Message> messages = Collections.singletonList(message);
        when(mongoTemplate.find(any(Query.class), eq(Message.class), eq(consumerGroup))).thenReturn(messages);

        // Test with processed = null
        List<Message> result = messageService.view(consumerGroup, null);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals(message.getContent(), result.get(0).getContent());
        assertEquals(message.getConsumerGroup(), result.get(0).getConsumerGroup());

        // Test with processed = "yes"
        result = messageService.view(consumerGroup, "yes");
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals(message.getContent(), result.get(0).getContent());
        assertEquals(message.getConsumerGroup(), result.get(0).getConsumerGroup());

        // Test with processed = "no"
        result = messageService.view(consumerGroup, "no");
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals(message.getContent(), result.get(0).getContent());
        assertEquals(message.getConsumerGroup(), result.get(0).getConsumerGroup());
    }
}
