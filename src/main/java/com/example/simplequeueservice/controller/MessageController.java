package com.example.simplequeueservice.controller;

import com.example.simplequeueservice.model.Message;
import com.example.simplequeueservice.model.MessageResponse;
import com.example.simplequeueservice.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/queue")
public class MessageController {

    private static final Logger logger = LoggerFactory.getLogger(MessageController.class);

    @Autowired
    private MessageService messageService;

@PostMapping("/push")
public MessageResponse push(@RequestHeader("consumerGroup") String consumerGroup, @RequestBody String content) {
    String messageId= UUID.randomUUID().toString();
    Message message = new Message(messageId, consumerGroup, content);
    logger.info("Received message with content: {}", content);
    Message pushedMessage = messageService.push(message);
    return new MessageResponse(pushedMessage);
}

@GetMapping("/pop")
public ResponseEntity<MessageResponse> pop(@RequestHeader("consumerGroup") String consumerGroup) {
    logger.info("Popping message from the queue");
    Optional<Message> message = messageService.pop(consumerGroup);
    return message.map(msg -> ResponseEntity.ok(new MessageResponse(msg)))
            .orElse(ResponseEntity.notFound().build());
}

@GetMapping("/view")
public List<Message> view(@RequestHeader("consumerGroup") String consumerGroup) {
    logger.info("Viewing all messages in the queue");
    return messageService.view(consumerGroup);
}
}
