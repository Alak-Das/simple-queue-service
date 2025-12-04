# Database Design Document

## 1. Introduction

This document describes the database design for the Simple Queue Service. The service primarily uses MongoDB for persistent message storage and Redis as an in-memory cache.

## 2. MongoDB Design

MongoDB is used as the primary data store for all messages. Each consumer group has its own dedicated collection in MongoDB, ensuring logical separation and multi-tenancy.

### 2.1 Data Model: `Message`

Messages are stored as documents in MongoDB collections. The structure of a `Message` document is as follows:

*   **Collection Name:** Dynamically determined by the `consumerGroup` header (e.g., `my-app-queue`).

*   **Document Structure:**
    ```json
    {
        "_id": "<UUID string>",
        "content": "<string (JSON or plain text)>",
        "consumerGroup": "<string>",
        "createdAt": "<ISODate>",
        "consumed": <boolean>,
        "_class": "com.al.simplequeueservice.model.Message" // Spring Data MongoDB specific field
    }
    ```

*   **Field Descriptions:**
    *   `_id`: `String` - A unique identifier for the message, generated as a UUID. This is the primary key in MongoDB.
    *   `content`: `String` - The actual content of the message. This can be a JSON string, plain text, or any other serializable string.
    *   `consumerGroup`: `String` - The logical grouping to which the message belongs. This value directly maps to the MongoDB collection name where the message is stored.
    *   `createdAt`: `ISODate` - A timestamp indicating when the message was initially pushed into the queue. This field is crucial for both ordering (popping the oldest message) and for applying the TTL index.
    *   `consumed`: `Boolean` - A flag indicating whether the message has been
