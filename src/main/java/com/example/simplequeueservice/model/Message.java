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
    @NotBlank(message = "Content is mandatory")
    private String content;
    private LocalDateTime createdAt;
    private boolean processed;

    public Message(String content) {
        this.content = content;
        this.createdAt = LocalDateTime.now();
        this.processed = false;
    }
}
