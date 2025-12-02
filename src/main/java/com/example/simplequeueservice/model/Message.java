package com.example.simplequeueservice.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Document(collection = "messages-queue")
public class Message {

    @Id
    private String id;
    @NotBlank(message = "Message Content is mandatory")
    private String content;
    @NotBlank(message = "Message consumerGroup is mandatory")
    String consumerGroup;
    private LocalDateTime createdAt;
    private boolean processed;

    public Message(String messageId, String consumerGroup, String content) {
        this.id = messageId;
        this.consumerGroup = consumerGroup;
        this.content = content;
        this.createdAt = LocalDateTime.now();
        this.processed = false;
    }
}
