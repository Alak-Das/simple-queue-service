package com.al.simplequeueservice.controller;

import com.al.simplequeueservice.exception.ErrorResponse;
import com.al.simplequeueservice.model.Message;
import com.al.simplequeueservice.model.MessageResponse;
import com.al.simplequeueservice.service.PopMessageService;
import com.al.simplequeueservice.service.PushMessageService;
import com.al.simplequeueservice.service.ViewMessageService;
import com.al.simplequeueservice.util.SQSConstants;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping(SQSConstants.QUEUE_URL)
public class MessageController {

    private static final Logger logger = LoggerFactory.getLogger(MessageController.class);

    @Autowired
    private PushMessageService pushMessageService;

    @Autowired
    private PopMessageService popMessageService;

    @Autowired
    private ViewMessageService viewMessageService;

    @Value("${no.of.message.allowed.to.fetch}")
    private long messageAllowedCount;

    @PostMapping(SQSConstants.PUSH_URL)
    public MessageResponse push(@RequestHeader(SQSConstants.CONSUMER_GROUP_HEADER) String consumerGroup, @RequestBody String content) {
        logger.info("Received message with content: {} for Consumer Group: {}", content, consumerGroup);
        String messageId = UUID.randomUUID().toString();
        Message message = new Message(messageId, consumerGroup, content);
        Message pushedMessage = pushMessageService.push(message);
        return new MessageResponse(pushedMessage);
    }

    @GetMapping(SQSConstants.POP_URL)
    public ResponseEntity<MessageResponse> pop(@RequestHeader(SQSConstants.CONSUMER_GROUP_HEADER) String consumerGroup) {
        logger.info("Received request to pop message from the Queue for Consumer Group: {}", consumerGroup);
        Optional<Message> message = popMessageService.pop(consumerGroup);
        return message.map(msg -> ResponseEntity.ok(new MessageResponse(msg)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(SQSConstants.VIEW_URL)
    public ResponseEntity<?> view(@RequestHeader(SQSConstants.CONSUMER_GROUP_HEADER) String consumerGroup, @RequestHeader(value = SQSConstants.MESSAGE_COUNT_HEADER) int messageCount,
                                  @RequestHeader(value = SQSConstants.CONSUMED, required = false) String consumed) {
        logger.info("Received request to view all messages in the Queue for Consumer Group: {}. Filter by consumed: {}", consumerGroup, StringUtils.isEmpty(consumed) ? "" : consumed);

        if (messageCount < 1 || messageCount > messageAllowedCount) {
            return ResponseEntity.badRequest().body(new ErrorResponse(400, "Bad Request", String.format(SQSConstants.MESSAGE_COUNT_VALIDATION_ERROR_MESSAGE, messageAllowedCount), SQSConstants.QUEUE_VIEW_URL));
        }

        List<Message> messages = viewMessageService.view(consumerGroup, messageCount, consumed);
        return ResponseEntity.ok(messages);
    }
}
