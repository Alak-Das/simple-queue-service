# Architecture Design Document

## 1. Introduction

This Architecture Design Document (ADD) provides a comprehensive overview of the Simple Queue Service (SQS), detailing its architectural style, components, interactions, and design considerations. The SQS is a lightweight, multi-tenant message queue built with Spring Boot, Redis for caching, and MongoDB for persistent storage.

## 2. Architectural Goals and Constraints

### 2.1 Goals

*   **Simplicity:** Easy to understand, deploy, and use.
*   **Scalability:** Ability to handle increasing message volumes and consumer groups.
*   **Reliability:** Ensures at-least-once message delivery and data durability.
*   **Performance:** Fast message processing, especially for push and pop operations, leveraging caching.
*   **Security:** Secure API access with role-based authorization.
*   **Observability:** Logging and API documentation for easier monitoring and integration.

### 2.2 Constraints

*   **Technology Stack:** Primarily Java 21, Spring Boot, Redis, MongoDB, Maven.
*   **Deployment Environment:** Designed for containerized environments (e.g., Docker).
*   **Message Size:** Assumed to be relatively small (JSON or plain text).
*   **Transactionality:** At-least-once delivery is targeted, but full distributed ACID transactions are not a primary requirement due to the nature of a message queue.

## 3. System Overview

The Simple Queue Service operates as a standalone microservice providing RESTful endpoints for managing messages. Clients interact with the service, which then orchestrates operations between an in-memory Redis cache and a persistent MongoDB database. Asynchronous processing is used for background database updates to minimize latency for client-facing operations.

**Key Interactions:**

1.  **Client-Service:** Clients send HTTP requests (push, pop, view) to the Spring Boot application.
2.  **Service-Cache (Redis):** The service interacts with Redis for quick message access and caching.
3.  **Service-Database (MongoDB):** The service persists messages to MongoDB and retrieves them when not available in the cache.
4.  **Asynchronous Tasks:** Background tasks (e.g., marking messages as consumed in MongoDB) run asynchronously to avoid blocking the main request thread.

## 4. Logical Architecture

The system is logically structured into several layers, each with distinct responsibilities.

### 4.1 Layers

*   **Presentation Layer (Controller):** Handles incoming HTTP requests, performs basic input validation, and delegates to the Service Layer.
    *   `MessageController`
*   **Service Layer (Business Logic):** Contains the core business logic for message queue operations. Orchestrates interactions with data access layers and cache.
    *   `PushMessageService`
    *   `PopMessageService`
    *   `ViewMessageService`
    *   `CacheService`
*   **Data Access Layer (Repository/Template):** Abstracts the details of interacting with MongoDB and Redis.
    *   `MongoTemplate` (implicitly used by services)
    *   `RedisTemplate` (used by `CacheService`)
*   **Configuration Layer:** Manages application-wide settings, security, and infrastructure beans.
    *   `AsyncConfig`
    *   `RedisConfig`
    *   `SecurityConfig`
*   **Model Layer:** Defines the data structures used throughout the application.
    *   `Message`
    *   `MessageResponse`
    *   `ErrorResponse`
*   **Utility Layer:** Provides common constants and helper functions.
    *   `SQSConstants`
*   **Exception Handling Layer:** Centralized exception handling to provide consistent error responses.
    *   `GlobalExceptionHandler`

### 4.2 Modules and Their Responsibilities

*   **`com.al.simplequeueservice.controller`:** Defines the RESTful API endpoints and handles request/response serialization.
*   **`com.al.simplequeueservice.service`:** Implements the core message queue logic, including caching, persistence, and message lifecycle management.
*   **`com.al.simplequeueservice.model`:** Contains plain old Java objects (POJOs) representing messages and API responses.
*   **`com.al.simplequeueservice.config`:** Configures Spring Boot components, security, asynchronous tasks, and database/cache connections.
*   **`com.al.simplequeueservice.exception`:** Provides custom exception classes and a global handler for consistent error reporting.
*   **`com.al.simplequeueservice.util`:** Stores application-wide constants and utility methods.

## 5. Viewpoints

### 5.1 Runtime View

1.  **Message Push Flow:**
    *   Client sends POST request to `/queue/push` with `consumerGroup` header and message content.
    *   `MessageController` receives the request.
    *   `PushMessageService` is invoked.
    *   `CacheService` adds the message to Redis (LPUSH) with a TTL.
    *   An asynchronous task is submitted to `taskExecutor`.
    *   The asynchronous task checks/creates a TTL index on the `consumerGroup` collection in MongoDB and then saves the message using `MongoTemplate`.
    *   `MessageController` returns `200 OK` with `MessageResponse`.

2.  **Message Pop Flow:**
    *   Client sends GET request to `/queue/pop` with `consumerGroup` header.
    *   `MessageController` receives the request.
    *   `PopMessageService` is invoked.
    *   `CacheService` attempts to pop the oldest message from Redis (RPOP).
        *   **If message found in Redis:** `PopMessageService` returns the message. An asynchronous task is submitted to `taskExecutor` to update the `consumed` status of this message in MongoDB.
        *   **If message not found in Redis:** `PopMessageService` queries MongoDB using `MongoTemplate.findAndModify` to atomically find the oldest unconsumed message, mark it as consumed, and return it. If no message is found, returns empty.
    *   `MessageController` returns `200 OK` with `MessageResponse` or `404 Not Found`.

3.  **Message View Flow:**
    *   Client sends GET request to `/queue/view` with `consumerGroup`, `messageCount`, and optional `consumed` headers.
    *   `MessageController` receives the request and performs basic validation on `messageCount`.
    *   `ViewMessageService` is invoked.
    *   **If `consumed` is "no" (unconsumed):**
        *   `CacheService.viewMessages()` retrieves messages from Redis.
        *   `MongoTemplate.find()` retrieves additional unconsumed messages from MongoDB, excluding those already found in Redis.
    *   **If `consumed` is "yes" (consumed) or not provided:**
        *   `MongoTemplate.find()` retrieves messages from MongoDB (with or without `consumed` filter).
    *   Results are combined, sorted by `createdAt`, and limited by `messageCount`.
    *   `MessageController` returns `200 OK` with a list of `Message` objects.

### 5.2 Deployment View

The application is packaged as a JAR file and can be deployed as a standalone Spring Boot application. The `Dockerfile` indicates containerization support, making it suitable for deployment in Docker environments, Kubernetes, or other cloud platforms.

**Dependencies:**

*   **Application Instance(s):** One or more instances of `simple-queue-service.jar`.
*   **MongoDB Instance(s):** A running MongoDB server (can be a replica set for high availability).
*   **Redis Instance(s):** A running Redis server (can be a Sentinel or Cluster setup for high availability).

**Scaling:**

*   **Application:** Can be scaled horizontally by running multiple instances. Load balancing would distribute requests among them.
*   **MongoDB:** Can be scaled using replica sets (for high availability and read scaling) and sharding (for write and storage scaling).
*   **Redis:** Can be scaled using Redis Sentinel (for high availability) or Redis Cluster (for sharding and higher throughput).

### 5.3 Security View

*   **Authentication:** HTTP Basic Authentication is used to verify the identity of clients. User credentials are configurable.
*   **Authorization:** Role-based access control (RBAC) is implemented using Spring Security. Different endpoints require specific roles (`USER` or `ADMIN`).
*   **CSRF Protection:** Disabled as it's a RESTful API primarily consumed by non-browser clients or clients handling their own CSRF tokens.
*   **Data in Transit:** Assumes TLS/SSL is handled at the infrastructure level (e.g., Load Balancer, API Gateway) if deployed in a production environment.
*   **Data at Rest:** MongoDB and Redis should be configured with appropriate security measures (e.g., authentication, encryption at rest) at the infrastructure level.

## 6. Quality Attributes

*   **Performance:** Achieved through Redis caching for frequently accessed messages and asynchronous processing for database writes.
*   **Scalability:** Designed for horizontal scaling of application instances, MongoDB, and Redis.
*   **Reliability:** At-least-once delivery guarantee for messages. Data durability provided by MongoDB. Redis provides quick recovery from failures due to its persistence options and high availability configurations.
*   **Maintainability:** Modular design, clear separation of concerns, and use of Spring Boot conventions.
*   **Security:** Implemented with Spring Security for authentication and authorization. Configurable user roles.
*   **Usability:** RESTful API with clear documentation via SpringDoc.

## 7. Future Considerations

*   **Message Ordering Guarantees:** While current implementation pops oldest message, for stricter ordering guarantees across distributed instances, a distributed lock or more advanced message broker might be needed.
*   **Dead Letter Queue (DLQ):** Implement a mechanism for messages that fail processing multiple times.
*   **Monitoring and Alerting:** Integrate with monitoring tools (e.g., Prometheus, Grafana) for deeper insights.
*   **Distributed Tracing:** Implement distributed tracing (e.g., with Zipkin/Sleuth) for better debugging in a microservices environment.
*   **Event Sourcing/CQRS:** For complex scenarios requiring event history or separate read/write models.
*   **More Robust Authentication:** Consider OAuth2/JWT for token-based authentication in more complex ecosystems.
*   **Configuration Management:** Externalize configuration further using Spring Cloud Config or similar solutions.
