package com.example.simplequeueservice.controller;

import com.example.simplequeueservice.model.Message;
import com.example.simplequeueservice.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;

@RestController
@RequestMapping("/queue")
public class MessageController {

    private static final Logger logger = LoggerFactory.getLogger(MessageController.class);

    @Autowired
    private MessageService messageService;

    @PostMapping("/push")
    public Message push(@RequestBody JsonNode request) {
        String content = request.toString();
        logger.info("Pushing message with content: {}", content);
        return messageService.push(content);
    }

    @GetMapping("/pop")
    public ResponseEntity<Message> pop() {
        logger.info("Popping message from the queue");
        Optional<Message> message = messageService.pop();
        return message.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/view")
    public List<Message> view() {
        logger.info("Viewing all messages in the queue");
        return messageService.view();
    }
}
