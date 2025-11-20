package com.example.simplequeueservice.service;

import com.example.simplequeueservice.model.Message;
import com.example.simplequeueservice.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

    @Autowired
    private MessageRepository messageRepository;

    public Message push(String content) {
        logger.info("Saving message with content: {}", content);
        Message message = new Message(content);
        return messageRepository.save(message);
    }

    public Optional<Message> pop() {
        logger.info("Popping oldest message from the queue");
        Optional<Message> oldestMessage = messageRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt"))
                .stream()
                .filter(message -> !message.isProcessed())
                .findFirst();

        oldestMessage.ifPresent(message -> {
            logger.info("Marking message with id {} as processed", message.getId());
            message.setProcessed(true);
            messageRepository.save(message);
        });

        return oldestMessage;
    }

    public List<Message> view() {
        logger.info("Viewing all messages in the queue");
        return messageRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt"));
    }
}
