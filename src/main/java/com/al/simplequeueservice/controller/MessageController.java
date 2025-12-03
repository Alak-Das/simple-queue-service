package com.al.simplequeueservice.controller;

import com.al.simplequeueservice.model.Message;
import com.al.simplequeueservice.model.MessageResponse;
import com.al.simplequeueservice.service.MessageService;
import org.apache.commons.lang3.StringUtils;
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
        logger.info("Received message with content: {} for Consumer Group: {}", content, consumerGroup);
        String messageId = UUID.randomUUID().toString();
        Message message = new Message(messageId, consumerGroup, content);
        Message pushedMessage = messageService.push(message);
        return new MessageResponse(pushedMessage);
    }

    @GetMapping("/pop")
    public ResponseEntity<MessageResponse> pop(@RequestHeader("consumerGroup") String consumerGroup) {
        logger.info("Received request to pop message from the Queue for Consumer Group: {}", consumerGroup);
        Optional<Message> message = messageService.pop(consumerGroup);
        return message.map(msg -> ResponseEntity.ok(new MessageResponse(msg)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/view")
    public List<Message> view(@RequestHeader("consumerGroup") String consumerGroup,
                              @RequestHeader(value = "consumed", required = false) String consumed) {
        logger.info("Received request to view all messages in the Queue for Consumer Group: {}. Filter by consumed: {}", consumerGroup, StringUtils.isEmpty(consumed)? "": consumed);
        return messageService.view(consumerGroup, consumed);
    }
}
