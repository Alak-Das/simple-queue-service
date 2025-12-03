package com.example.simplequeueservice.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import java.util.Date;

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
    private Date createdAt;
    private boolean processed;

    public Message(String messageId, String consumerGroup, String content) {
        this.id = messageId;
        this.consumerGroup = consumerGroup;
        this.content = content;
        this.createdAt = new Date();
        this.processed = false;
    }
}
