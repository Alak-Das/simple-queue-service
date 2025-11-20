package com.example.simplequeueservice.service;

import com.example.simplequeueservice.model.Message;
import com.example.simplequeueservice.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MessageServiceTest {

    @InjectMocks
    private MessageService messageService;

    @Mock
    private MessageRepository messageRepository;

    @Test
    public void testPush() {
        Message message = new Message("Test message");
        when(messageRepository.save(any(Message.class))).thenReturn(message);

        Message result = messageService.push("Test message");
        assertEquals("Test message", result.getContent());
    }

    @Test
    public void testPop() {
        Message message = new Message("Test message");
        when(messageRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt"))).thenReturn(Arrays.asList(message));

        Optional<Message> result = messageService.pop();
        assertEquals("Test message", result.get().getContent());
    }

    @Test
    public void testView() {
        Message message = new Message("Test message");
        when(messageRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt"))).thenReturn(Arrays.asList(message));

        assertEquals(1, messageService.view().size());
    }
}
