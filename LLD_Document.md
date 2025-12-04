# Low-Level Design (LLD) Document

## 1. Introduction

This Low-Level Design (LLD) document provides detailed insights into the internal workings, class structures, and algorithmic logic of the Simple Queue Service (SQS). It complements the High-Level Design (HLD) and Architecture Design Document (ADD) by elaborating on the implementation specifics of individual components and modules.

## 2. Core Components and Class Design

### 2.1 Model Classes

*   **`com.al.simplequeueservice.model.Message`**
    *   **Purpose:** Represents a single message entity within the queue system. This object is persisted in MongoDB and cached in Redis.
    *   **Annotations:** `@Getter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Document(collection = "messages-queue")`.
    *   **Fields:**
        *   `@Id private String id;`: Unique identifier for the message, typically a UUID, auto-generated at the time of pushing.
        *   `@NotBlank(message = "Message Content is mandatory") private String content;`: The actual payload of the message, stored as a String (can be JSON, plain text, etc.). Validation ensures it's not blank.
        *   `@NotBlank(message = "Message consumerGroup is mandatory") private String consumerGroup;`: The identifier for the queue/consumer group this message belongs to. This maps directly to a MongoDB collection name. Validation ensures it's not blank.
        *   `private Date createdAt;`: Timestamp marking when the message was created and pushed into the system. Used for ordering and TTL indexing.
        *   `private boolean consumed;`: A flag indicating if the message has been popped from the queue. `false` by default, set to `true` upon successful `pop` operation.
    *   **Constructors:**
        *   `public Message(String messageId, String consumerGroup, String content)`: Convenience constructor for new messages, setting `createdAt` to current date and `consumed` to `false`.
    *   **Methods:**
        *   `public Message markConsumed()`: Returns a *new* `Message` instance with the `consumed` flag set to `true`, maintaining immutability where possible while allowing status updates.
        *   `equals(Object o)` and `hashCode()`: Implemented based on `id` for proper collection behavior.

*   **`com.al.simplequeueservice.model.MessageResponse`**
    *   **Purpose:** A Data Transfer Object (DTO) used for sending message details back to the client in API responses. It excludes internal fields like `consumerGroup` and `consumed`.
    *   **Annotations:** `@Data`, `@NoArgsConstructor`.
    *   **Fields:**
        *   `private String id;`: Unique identifier of the message.
        *   `private String content;`: The payload of the message.
        *   `private LocalDateTime createdAt;`: Timestamp of message creation, converted from `Date` to `LocalDateTime` for API consistency.
    *   **Constructors:**
        *   `public MessageResponse(Message message)`: Maps a `Message` entity to a `MessageResponse` DTO.

*   **`com.al.simplequeueservice.exception.ErrorResponse`**
    *   **Purpose:** Standardized error response format for API clients.
    *   **Annotations:** `@Data`.
    *   **Fields:** `timestamp` (LocalDateTime), `status` (int HTTP status code), `error` (String HTTP status phrase), `message` (String detailed error message), `path` (String request URI).
    *   **Constructors:** `public ErrorResponse(int status, String error, String message, String path)`.

### 2.2 Controller Class

*   **`com.al.simplequeueservice.controller.MessageController`**
    *   **Purpose:** Handles incoming REST API requests related to message queue operations.
    *   **Annotations:** `@RestController`, `@RequestMapping("/queue")`.
    *   **Dependencies:** `@Autowired PushMessageService`, `@Autowired PopMessageService`, `@Autowired ViewMessageService`.
    *   **Endpoints:**
        *   **`POST /queue/push`:**
            *   **Input:** `@RequestHeader(SQSConstants.CONSUMER_GROUP_HEADER) String consumerGroup`, `@RequestBody String content`.
            *   **Logic:** Generates a UUID for `messageId`. Creates a `Message` object. Calls `pushMessageService.push(message)`. Returns `200 OK` with `MessageResponse`.
        *   **`GET /queue/pop`:**
            *   **Input:** `@RequestHeader(SQSConstants.CONSUMER_GROUP_HEADER) String consumerGroup`.
            *   **Logic:** Calls `popMessageService.pop(consumerGroup)`. If `Optional<Message>` is present, returns `200 OK` with `MessageResponse`. Otherwise, returns `404 Not Found`.
        *   **`GET /queue/view`:**
            *   **Input:** `@RequestHeader(SQSConstants.CONSUMER_GROUP_HEADER) String consumerGroup`, `@RequestHeader(value = SQSConstants.MESSAGE_COUNT_HEADER) int messageCount`, `@RequestHeader(value = SQSConstants.CONSUMED, required = false) String consumed`.
            *   **Logic:** Validates `messageCount` against `no.of.message.allowed.to.fetch` from properties. If invalid, returns `400 Bad Request`. Calls `viewMessageService.view(consumerGroup, messageCount, consumed)`. Returns `200 OK` with `List<Message>`.

### 2.3 Service Classes

*   **`com.al.simplequeueservice.service.PushMessageService`**
    *   **Purpose:** Encapsulates the business logic for pushing messages.
    *   **Dependencies:** `MongoClient`, `@Value("${spring.data.mongodb.database}") String mongoDB`, `@Value("${persistence.duration.minutes}") long expireMinutes`, `MongoTemplate`, `CacheService`, `@Qualifier("taskExecutor") Executor taskExecutor`.
    *   **Method: `push(Message message)`**
        1.  Add message to Redis cache: `cacheService.addMessage(message)`.
        2.  Asynchronously save message to MongoDB:
            *   Submits a task to `taskExecutor`.
            *   Inside the async task: Calls `createTTLIndex(message)` to ensure a TTL index exists on the `createdAt` field for the `consumerGroup`'s collection.
            *   Saves the `message` to its respective `consumerGroup` collection using `mongoTemplate.save(message, message.getConsumerGroup())`.
    *   **Method: `private void createTTLIndex(Message message)`**
        1.  Retrieves the MongoDB collection for the given `consumerGroup`.
        2.  Checks if a TTL index already exists on the `createdAt` field.
        3.  If not, creates an index on `createdAt` with an `expireAfterSeconds` option derived from `expireMinutes`.

*   **`com.al.simplequeueservice.service.PopMessageService`**
    *   **Purpose:** Encapsulates the business logic for popping messages.
    *   **Dependencies:** `MongoTemplate`, `CacheService`, `@Qualifier("taskExecutor") Executor taskExecutor`.
    *   **Method: `pop(String consumerGroup)`**
        1.  **Try Cache First:** Calls `cacheService.popMessage(consumerGroup)`.
        2.  **If message found in cache:**
            *   Submits an asynchronous task to `taskExecutor` to call `updateMessageInMongo(cachedMessage.getId(), consumerGroup)`.
            *   Returns `Optional.of(cachedMessage)`.
        3.  **If message not found in cache:**
            *   Constructs a `Query` to find the oldest unconsumed message (`consumed: false`, sorted by `createdAt` ASC).
            *   Constructs an `Update` to set `consumed: true`.
            *   Uses `mongoTemplate.findAndModify(query, update, options, Message.class, consumerGroup)` to atomically retrieve and update the message.
            *   Returns `Optional.ofNullable(message)` based on the result of `findAndModify`.
    *   **Method: `private void updateMessageInMongo(String messageId, String consumerGroup)`**
        1.  Constructs a `Query` to find the message by `id`.
        2.  Constructs an `Update` to set `consumed: true`.
        3.  Uses `mongoTemplate.findAndModify()` to update the message without returning the modified document.

*   **`com.al.simplequeueservice.service.ViewMessageService`**
    *   **Purpose:** Encapsulates the business logic for viewing messages.
    *   **Dependencies:** `MongoTemplate`, `CacheService`.
    *   **Method: `view(String consumerGroup, int messageCount, String consumed)`**
        1.  Initializes an empty `List<Message> combinedMessages`.
        2.  Creates a `Query` for MongoDB.
        3.  **If `consumed` is "no" (unconsumed messages requested):**
            *   Retrieves cached messages: `cacheService.viewMessages(consumerGroup).stream().limit(messageCount).toList()`.
            *   Adds cached messages to `combinedMessages`.
            *   Creates a `Set<String>` of `cachedMessageIds`.
            *   If `cachedMessageIds.size() < messageCount`, adds a `Criteria.where(ID).nin(cachedMessageIds)` to the MongoDB `query` to exclude already-cached messages. If all messages are found in cache, sorts and returns `combinedMessages` directly.
            *   Adds `Criteria.where(CONSUMED).is(false)` to the MongoDB `query`.
        4.  **If `consumed` is "yes" (consumed messages requested):**
            *   Adds `Criteria.where(CONSUMED).is(true)` to the MongoDB `query`.
        5.  Sets `query.limit(messageCount - combinedMessages.size())` to fetch the remaining messages from MongoDB if any were retrieved from cache.
        6.  Retrieves messages from MongoDB: `mongoTemplate.find(query, Message.class, consumerGroup)`.
        7.  Adds MongoDB messages to `combinedMessages`.
        8.  Sorts `combinedMessages` by `createdAt`.
        9.  Returns the sorted and combined list.

*   **`com.al.simplequeueservice.service.CacheService`**
    *   **Purpose:** Abstraction layer for Redis interactions.
    *   **Dependencies:** `RedisTemplate<String, Object>`, `@Value("${cache.ttl.minutes}") long redisCacheTtlMinutes`.
    *   **Method: `addMessage(Message message)`**
        1.  Constructs a Redis key: `SQSConstants.CACHE_PREFIX + message.getConsumerGroup()`.
        2.  `redisTemplate.opsForList().leftPush(key, message)`: Pushes the message to the head of the list.
        3.  `redisTemplate.expire(key, Duration.ofMinutes(redisCacheTtlMinutes))`: Sets a TTL for the Redis list.
    *   **Method: `popMessage(String consumerGroup)`**
        1.  Constructs the Redis key.
        2.  `redisTemplate.opsForList().rightPop(key)`: Pops and returns the message from the tail of the list (oldest).
    *   **Method: `viewMessages(String consumerGroup)`**
        1.  Constructs the Redis key.
        2.  `redisTemplate.opsForList().range(key, 0, -1)`: Retrieves all messages from the Redis list.
        3.  Filters out non-`Message` objects and casts to `Message`.

### 2.4 Configuration Classes

*   **`com.al.simplequeueservice.config.AsyncConfig`**
    *   **Purpose:** Configures the asynchronous task executor for non-blocking operations.
    *   **Annotations:** `@Configuration`, `@EnableAsync`.
    *   **Bean: `taskExecutor()`:** Returns a `ThreadPoolTaskExecutor` with configurable `corePoolSize`, `maxPoolSize`, `queueCapacity`, and `threadNamePrefix` defined in `SQSConstants`.

*   **`com.al.simplequeueservice.config.RedisConfig`**
    *   **Purpose:** Configures Redis connection settings and cache managers.
    *   **Annotations:** `@Configuration`.
    *   **Dependencies:** `@Value("${cache.ttl.minutes}") long redisCacheTtlMinutes`.
    *   **Bean: `cacheManager(RedisConnectionFactory redisConnectionFactory)`:** Configures `RedisCacheManager` with a default `RedisCacheConfiguration` that includes a configurable `entryTtl` and `GenericJackson2JsonRedisSerializer` for value serialization.
    *   **Bean: `redisTemplate(RedisConnectionFactory connectionFactory)`:** Creates a `RedisTemplate<String, Object>` with `StringRedisSerializer` for keys and `GenericJackson2JsonRedisSerializer` for values.

*   **`com.al.simplequeueservice.config.SecurityConfig`**
    *   **Purpose:** Configures Spring Security for authentication and authorization.
    *   **Annotations:** `@Configuration`, `@EnableWebSecurity`.
    *   **Dependencies:** `@Value` for user/admin credentials from `application.properties`.
    *   **Bean: `securityFilterChain(HttpSecurity http)`:**
        *   Configures authorization rules: `/queue/push` and `/queue/pop` require `USER` or `ADMIN` roles. `/queue/view` requires `ADMIN` role. All other requests require authentication.
        *   Enables HTTP Basic Authentication (`httpBasic(withDefaults())`).
        *   Disables CSRF protection (`csrf(AbstractHttpConfigurer::disable)`) as it's a stateless REST API.
    *   **Bean: `userDetailsService(PasswordEncoder passwordEncoder)`:** Configures `InMemoryUserDetailsManager` with a `USER` and an `ADMIN` user, whose passwords are encoded using `BCryptPasswordEncoder`.
    *   **Bean: `passwordEncoder()`:** Provides a `BCryptPasswordEncoder` bean.

### 2.5 Exception Handling

*   **`com.al.simplequeueservice.exception.GlobalExceptionHandler`**
    *   **Purpose:** Centralized exception handling across all `@Controller` classes.
    *   **Annotations:** `@ControllerAdvice`.
    *   **Methods:**
        *   `handleException(Exception ex, HttpServletRequest request)`: Catches generic `Exception` and returns `500 Internal Server Error` with an `ErrorResponse`.
        *   `handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request)`: Catches `IllegalArgumentException` and returns `400 Bad Request` with an `ErrorResponse`.

### 2.6 Utility Class

*   **`com.al.simplequeueservice.util.SQSConstants`**
    *   **Purpose:** Stores static final constants used throughout the application to avoid hardcoding and improve maintainability.
    *   **Contents:** Field names (`ID`, `CONSUMED`, `CREATED_AT`), async executor parameters (`CORE_POOL_SIZE`, `MAX_POOL_SIZE`, `QUEUE_CAPACITY`, `THREAD_NAME_PREFIX`), API URLs (`QUEUE_BASE_URL`, `PUSH_URL`, `POP_URL`, `VIEW_URL`), roles (`USER_ROLE`, `ADMIN_ROLE`, `HAS_ADMIN_ROLE`), HTTP header names (`CONSUMER_GROUP_HEADER`, `MESSAGE_COUNT_HEADER`, `CONSUMED`), validation messages, and Redis cache prefix (`CACHE_PREFIX`).

## 3. Data Flow and Algorithms

Detailed data flow and sequence diagrams are provided in the [Architecture Design Document](#).

### 3.1 Push Operation Algorithm

1.  Generate a unique `messageId` (UUID).
2.  Create a `Message` object with `messageId`, `consumerGroup`, `content`, current `createdAt` timestamp, and `consumed=false`.
3.  Call `CacheService.addMessage(message)` to add the message to Redis (LPUSH) and set its TTL.
4.  Submit an asynchronous task to `taskExecutor`:
    a.  Check for and create a MongoDB TTL index on `createdAt` for the `consumerGroup` collection if it doesn't exist, expiring after `persistence.duration.minutes`.
    b.  Save the `Message` object to the corresponding MongoDB `consumerGroup` collection using `MongoTemplate.save()`.
5.  Return the `MessageResponse` to the client.

### 3.2 Pop Operation Algorithm

1.  Call `CacheService.popMessage(consumerGroup)` to attempt to retrieve the oldest message from Redis (RPOP).
2.  **If a message is returned from Redis:**
    a.  Submit an asynchronous task to `taskExecutor` to update the message's `consumed` status in MongoDB: `PopMessageService.updateMessageInMongo(messageId, consumerGroup)`.
    b.  Return the message to the client.
3.  **If no message is returned from Redis:**
    a.  Construct a MongoDB `Query`:
        *   `Criteria.where(CONSUMED).is(false)` to find unprocessed messages.
        *   `Sort.by(Sort.Direction.ASC, CREATED_AT)` to get the oldest message.
    b.  Construct a MongoDB `Update` to set `CONSUMED` to `true`.
    c.  Execute `mongoTemplate.findAndModify(query, update, options, Message.class, consumerGroup)` with `returnNew(true)` option.
    d.  If a message is returned by `findAndModify`, return it to the client.
    e.  If `findAndModify` returns null, return an empty `Optional` (indicating no messages to pop).

### 3.3 View Operation Algorithm

1.  Initialize an empty list `combinedMessages`.
2.  Create a base MongoDB `Query`.
3.  **If `consumed` header is "no" (request for unconsumed messages):**
    a.  Retrieve a limited number of messages from Redis cache using `CacheService.viewMessages(consumerGroup)`.
    b.  Add these cached messages to `combinedMessages`.
    c.  Collect the `id`s of cached messages into a `Set<String> cachedMessageIds`.
    d.  If `cachedMessageIds.size()` is less than `messageCount` (meaning more messages are needed):
        *   Add `Criteria.where(ID).nin(cachedMessageIds)` to the MongoDB `Query` to exclude messages already found in cache.
    e.  Add `Criteria.where(CONSUMED).is(false)` to the MongoDB `Query`.
4.  **If `consumed` header is "yes" (request for consumed messages):**
    a.  Add `Criteria.where(CONSUMED).is(true)` to the MongoDB `Query`.
5.  **If `consumed` header is not provided (request for all messages):**
    a.  No `consumed` criteria is added to the MongoDB `Query`.
6.  Set the MongoDB `Query` limit: `query.limit(messageCount - combinedMessages.size())` (to fetch only the remaining required messages after checking cache).
7.  Execute `mongoTemplate.find(query, Message.class, consumerGroup)` to retrieve messages from MongoDB.
8.  Add the retrieved MongoDB messages to `combinedMessages`.
9.  Sort `combinedMessages` by `createdAt` in ascending order.
10. Return the `combinedMessages` list to the client.

## 4. Error Handling

The application employs a `@ControllerAdvice` (`GlobalExceptionHandler`) for centralized error handling, ensuring consistent error responses across the API. Specific exceptions like `IllegalArgumentException` are mapped to `400 Bad Request`, while other uncaught exceptions default to `500 Internal Server Error`. Error details are provided in an `ErrorResponse` DTO.

## 5. Security Details

Spring Security is configured to enforce HTTP Basic Authentication. Users are defined in-memory with `USER` and `ADMIN` roles, whose credentials are externalized in `application.properties`. Endpoint-level authorization rules are applied:

*   `/queue/push`, `/queue/pop`: Accessible by `USER` or `ADMIN` roles.
*   `/queue/view`: Accessible only by `ADMIN` role.

CSRF protection is disabled for the RESTful API.

## 6. Configuration Details

Key configurations are managed via `application.properties` and environment variables:

*   **MongoDB:** `spring.data.mongodb.uri`, `spring.data.mongodb.database`, `persistence.duration.minutes` (for TTL index).
*   **Redis:** `spring.redis.host`, `spring.redis.port`, `cache.ttl.minutes` (for cache entry TTL).
*   **Security:** `security.user.username`, `security.user.password`, `security.admin.username`, `security.admin.password`.
*   **Application:** `server.port`, `no.of.message.allowed.to.fetch`.

## 7. Performance Optimizations

*   **Redis Caching:** Reduces database load and improves response times for frequently accessed messages (`push` and `pop`).
*   **Asynchronous Database Operations:** Offloads potentially blocking MongoDB write operations to a separate thread pool (`taskExecutor`), ensuring the main API thread remains responsive.
*   **MongoDB TTL Indexes:** Automates cleanup of old messages, maintaining database performance.
*   **MongoDB `findAndModify`:** Ensures atomic pop operations from the database, preventing race conditions.
*   **Indexed Queries:** MongoDB queries for `pop` and `view` operations leverage indexes on `createdAt` and `consumed` fields for efficient data retrieval.
