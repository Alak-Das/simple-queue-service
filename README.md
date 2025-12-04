# Simple Queue Service

## Project Overview

This project implements a simple, lightweight message queue service using Spring Boot, designed to handle message pushing, popping, and viewing across different consumer groups. It leverages Redis for caching and MongoDB for persistent storage with a Time-To-Live (TTL) mechanism, ensuring efficient and scalable message management.

### Key Features:

*   **Multi-tenancy:** Supports isolated queues for different consumer groups.
*   **RESTful API:** Provides a clean and intuitive API for all queue operations.
*   **Hybrid Persistence:** Utilizes Redis for fast caching and MongoDB for durable message storage.
*   **At-Least-Once Delivery:** Messages are marked as processed to ensure reliable delivery.
*   **Configurable Message Retention:** MongoDB TTL index automatically deletes old messages.
*   **Asynchronous Operations:** Improves performance by offloading database writes to a separate thread pool.
*   **Robust Error Handling:** Global exception handling ensures graceful error responses.
*   **Security:** Role-based access control using Spring Security.
*   **API Documentation:** Integrated with SpringDoc for OpenAPI 3 documentation.

## Architecture Design

### High-Level Design (HLD)

The Simple Queue Service (SQS) is designed as a Spring Boot application that acts as an intermediary between clients and persistent/cached message storage. It exposes a RESTful API, secured by Spring Security, allowing clients to interact with message queues specific to their `consumerGroup`. Messages are initially pushed to a Redis cache for immediate availability and then asynchronously persisted to MongoDB. For popping messages, the service prioritizes fetching from Redis, falling back to MongoDB if the message is not found in the cache. A dedicated asynchronous executor handles background tasks like updating message status in MongoDB and creating TTL indexes.

**Components:**

*   **Client:** Interacts with the SQS API via HTTP requests.
*   **Spring Boot Application:** The core of the service, encapsulating:
    *   **MessageController:** Exposes REST endpoints for `push`, `pop`, and `view` operations.
    *   **Service Layer (`PushMessageService`, `PopMessageService`, `ViewMessageService`, `CacheService`):** Contains the business logic for message handling, caching, and persistence.
    *   **Configuration (`RedisConfig`, `SecurityConfig`, `AsyncConfig`):** Manages connections, security, and asynchronous task execution.
    *   **Models (`Message`, `MessageResponse`):** Define the structure of messages and API responses.
    *   **Exception Handling (`GlobalExceptionHandler`, `ErrorResponse`):** Provides a centralized mechanism for handling application-wide exceptions.
*   **Redis:** In-memory data store used as a fast cache for recently pushed and unprocessed messages. Configured with a configurable TTL.
*   **MongoDB:** Document database for durable message persistence. Each `consumerGroup` corresponds to a separate collection, and a TTL index ensures automatic message expiration.

**Architecture Diagram:**

```mermaid
graph TD
    A[Client] -->|HTTP/S| B(Spring Boot Application)
    B -->|Read/Write (Cache First)| C[Redis Cache]
    B -->|Read/Write (Persistent)| D[MongoDB Database]

    subgraph Spring Boot Application
        B_C[MessageController]
        B_S[Service Layer]
        B_Con[Configuration]
        B_M[Models]
        B_E[Exception Handling]

        B_C -->> B_S
        B_S -->> B_Con
        B_S -->> B_M
        B_S -->> B_E
    end

    C -->> B_S
    D -->> B_S

    B_S -- Async Update --> D
```

### Low-Level Design (LLD)

#### Class Design

*   **`com.al.simplequeueservice.model.Message`:**
    *   Represents a message in the queue. 
    *   Fields: `id` (String, `@Id`), `content` (String, `@NotBlank`), `consumerGroup` (String, `@NotBlank`), `createdAt` (Date), `consumed` (boolean).
    *   Provides constructor for new messages and `markConsumed()` method to create a new instance with `consumed` status set to true.
    *   Uses Lombok for boilerplate code reduction (`@Getter`, `@NoArgsConstructor`, `@AllArgsConstructor`).
*   **`com.al.simplequeueservice.model.MessageResponse`:**
    *   DTO for API responses, containing `id`, `content`, and `createdAt` (converted to `LocalDateTime`).
*   **`com.al.simplequeueservice.controller.MessageController`:**
    *   REST controller with `@RequestMapping("/queue")`.
    *   Injects `PushMessageService`, `PopMessageService`, and `ViewMessageService`.
    *   **`push(@RequestHeader String consumerGroup, @RequestBody String content)` (POST /queue/push):** Generates a UUID, creates a `Message` object, calls `pushMessageService.push()`, and returns `MessageResponse`.
    *   **`pop(@RequestHeader String consumerGroup)` (GET /queue/pop):** Calls `popMessageService.pop()`. Returns `200 OK` with `MessageResponse` if a message is found, else `404 Not Found`.
    *   **`view(@RequestHeader String consumerGroup, @RequestHeader int messageCount, @RequestHeader(required = false) String consumed)` (GET /queue/view):** Validates `messageCount`, calls `viewMessageService.view()`, and returns `200 OK` with a list of `Message` objects. Handles invalid `messageCount` with `400 Bad Request`.
*   **`com.al.simplequeueservice.service.PushMessageService`:**
    *   Handles pushing messages.
    *   `push(Message message)`: Adds message to Redis via `CacheService.addMessage()`, then asynchronously saves to MongoDB via `taskExecutor` using `MongoTemplate.save()`. Ensures a TTL index on `createdAt` in MongoDB for the specific `consumerGroup` collection.
*   **`com.al.simplequeueservice.service.PopMessageService`:**
    *   Handles popping messages.
    *   `pop(String consumerGroup)`: Attempts to `popMessage()` from `CacheService`. If found, it asynchronously calls `updateMessageInMongo()` to mark it consumed in MongoDB. If not in cache, it uses `MongoTemplate.findAndModify()` to atomically retrieve and mark the oldest unprocessed message from MongoDB as consumed.
*   **`com.al.simplequeueservice.service.ViewMessageService`:**
    *   Handles viewing messages.
    *   `view(String consumerGroup, int messageCount, String consumed)`: Retrieves messages from `CacheService.viewMessages()` and then from MongoDB (`MongoTemplate.find()`), filtering by `consumed` status and excluding duplicates found in cache. Sorts results by `createdAt`.
*   **`com.al.simplequeueservice.service.CacheService`:**
    *   Interacts with Redis using `RedisTemplate<String, Object>`.
    *   `addMessage(Message message)`: Pushes a message to the left of a Redis list (key: `CACHE_PREFIX + consumerGroup`) and sets a configurable TTL.
    *   `popMessage(String consumerGroup)`: Pops a message from the right of the Redis list.
    *   `viewMessages(String consumerGroup)`: Retrieves all messages from the Redis list.
*   **`com.al.simplequeueservice.config.AsyncConfig`:**
    *   Configures a `ThreadPoolTaskExecutor` bean (`taskExecutor`) for asynchronous operations (core pool size, max pool size, queue capacity, thread name prefix defined in `SQSConstants`).
*   **`com.al.simplequeueservice.config.RedisConfig`:**
    *   Configures `RedisConnectionFactory`, `CacheManager`, and `RedisTemplate`.
    *   Sets up `RedisCacheConfiguration` with a configurable TTL (`cache.ttl.minutes`) and `GenericJackson2JsonRedisSerializer` for value serialization.
*   **`com.al.simplequeueservice.config.SecurityConfig`:**
    *   Configures HTTP Basic Authentication and role-based authorization using `InMemoryUserDetailsManager`.
    *   Endpoints `/queue/push` and `/queue/pop` require `USER` or `ADMIN` roles.
    *   Endpoint `/queue/view` requires `ADMIN` role.
    *   Disables CSRF.
    *   User details (username/password) are configurable via `application.properties`.
*   **`com.al.simplequeueservice.exception.GlobalExceptionHandler`:**
    *   Uses `@ControllerAdvice` to provide centralized exception handling.
    *   Handles generic `Exception` (returns `500 Internal Server Error`) and `IllegalArgumentException` (returns `400 Bad Request`).
    *   Returns structured `ErrorResponse` objects.
*   **`com.al.simplequeueservice.util.SQSConstants`:**
    *   Contains various constants used throughout the application, including API paths, roles, header names, and thread pool configuration values.

#### Sequence Diagrams

**Push Operation:**

```mermaid
sequenceDiagram
    participant Client
    participant MessageController
    participant PushMessageService
    participant CacheService
    participant MongoTemplate
    participant MongoDB
    participant TaskExecutor

    Client->>+MessageController: POST /queue/push (consumerGroup, messageContent)
    MessageController->>PushMessageService: push(message)
    PushMessageService->>CacheService: addMessage(message)
    CacheService-->>PushMessageService: 
    PushMessageService->>TaskExecutor: execute(() -> saveToMongoDB(message))
    TaskExecutor->>PushMessageService: createTTLIndex(message)
    PushMessageService->>MongoTemplate: save(message, consumerGroup)
    MongoTemplate->>MongoDB: insert(document)
    MongoDB-->>MongoTemplate: 
    MongoTemplate-->>PushMessageService: 
    PushMessageService-->>-MessageController: pushedMessage
    MessageController-->>-Client: 200 OK (MessageResponse)
```

**Pop Operation:**

```mermaid
sequenceDiagram
    participant Client
    participant MessageController
    participant PopMessageService
    participant CacheService
    participant MongoTemplate
    participant MongoDB
    participant TaskExecutor

    Client->>+MessageController: GET /queue/pop (consumerGroup)
    MessageController->>PopMessageService: pop(consumerGroup)
    PopMessageService->>CacheService: popMessage(consumerGroup)
    alt Message in Cache
        CacheService-->>PopMessageService: cachedMessage
        PopMessageService->>TaskExecutor: execute(() -> updateMessageInMongo(messageId, consumerGroup))
        TaskExecutor->>PopMessageService: updateMessageInMongo(messageId, consumerGroup)
        PopMessageService->>MongoTemplate: findAndModify(queryById, updateConsumed)
        MongoTemplate->>MongoDB: updateDocument
        MongoDB-->>MongoTemplate: 
        MongoTemplate-->>PopMessageService: 
        PopMessageService-->>-MessageController: cachedMessage
    else Message not in Cache
        CacheService-->>PopMessageService: null
        PopMessageService->>MongoTemplate: findAndModify(queryOldestUnconsumed, updateConsumed)
        MongoTemplate->>MongoDB: findAndModifyDocument
        MongoDB-->>MongoTemplate: modifiedMessage
        MongoTemplate-->>PopMessageService: modifiedMessage
        PopMessageService-->>-MessageController: modifiedMessage
    end
    MessageController-->>-Client: 200 OK (MessageResponse) / 404 Not Found
```

## API Design Document

### Base URL

`/queue`

### Authentication

HTTP Basic Authentication is required for all endpoints. Configurable users (`USER` and `ADMIN`) are defined in `application.properties`.

### Endpoints

#### 1. Push a Message

*   **Method:** `POST`
*   **URL:** `/queue/push`
*   **Roles Required:** `USER`, `ADMIN`
*   **Headers:**
    *   `consumerGroup`: `String` (Required) - The name of the consumer group.
*   **Request Body:**
    *   `String` (JSON or plain text) - The content of the message.
    ```json
    {
        "data": "some_json_data",
        "timestamp": "2025-05-12T12:00:00Z"
    }
    ```
*   **Success Response (200 OK):**
    *   `application/json`
    ```json
    {
        "id": "uuid-of-message",
        "content": "{\"data\":\"some_json_data\",\"timestamp\":\"2025-05-12T12:00:00Z\"}",
        "createdAt": "2025-05-12T12:00:00.000000"
    }
    ```
*   **Error Responses:**
    *   `400 Bad Request`: Invalid input (e.g., missing `consumerGroup` header, invalid content).
    *   `401 Unauthorized`: Missing or invalid authentication credentials.
    *   `403 Forbidden`: Authenticated user does not have `USER` or `ADMIN` role.
    *   `500 Internal Server Error`: Generic server error.

#### 2. Pop a Message

*   **Method:** `GET`
*   **URL:** `/queue/pop`
*   **Roles Required:** `USER`, `ADMIN`
*   **Headers:**
    *   `consumerGroup`: `String` (Required) - The name of the consumer group.
*   **Request Body:** None
*   **Success Response (200 OK):**
    *   `application/json` - Returns the oldest unprocessed message.
    ```json
    {
        "id": "uuid-of-message",
        "content": "{\"data\":\"some_json_data\",\"timestamp\":\"2025-05-12T12:00:00Z\"}",
        "createdAt": "2025-05-12T12:00:00.000000"
    }
    ```
*   **Error Responses:**
    *   `404 Not Found`: No unprocessed messages found in the specified `consumerGroup`.
    *   `401 Unauthorized`: Missing or invalid authentication credentials.
    *   `403 Forbidden`: Authenticated user does not have `USER` or `ADMIN` role.
    *   `500 Internal Server Error`: Generic server error.

#### 3. View Messages

*   **Method:** `GET`
*   **URL:** `/queue/view`
*   **Roles Required:** `ADMIN`
*   **Headers:**
    *   `consumerGroup`: `String` (Required) - The name of the consumer group.
    *   `messageCount`: `int` (Required) - The maximum number of messages to retrieve (1 to `no.of.message.allowed.to.fetch` from `application.properties`).
    *   `consumed`: `String` (Optional) - Filter messages by status.
