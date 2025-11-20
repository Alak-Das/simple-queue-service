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
        Message message = new Message(content);
        when(mongoTemplate.save(any(Message.class), eq(consumerGroup))).thenReturn(message);

        Message result = messageService.push(consumerGroup, content);
        assertNotNull(result);
        assertEquals(content, result.getContent());
    }

    @Test
    void pop() {
        String consumerGroup = "testGroup";
        Message message = new Message("testContent");
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Message.class), eq(consumerGroup))).thenReturn(message);

        Optional<Message> result = messageService.pop(consumerGroup);
        assertTrue(result.isPresent());
        assertEquals(message.getContent(), result.get().getContent());
    }

    @Test
    void view() {
        String consumerGroup = "testGroup";
        Message message = new Message("testContent");
        List<Message> messages = Collections.singletonList(message);
        when(mongoTemplate.findAll(Message.class, consumerGroup)).thenReturn(messages);

        List<Message> result = messageService.view(consumerGroup);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals(message.getContent(), result.get(0).getContent());
    }
}
