# Simple Queue Service

## Project Overview

This project is a simple, lightweight message queue service built with Spring Boot. It provides a RESTful API for pushing, popping, and viewing messages in different consumer groups. The service is designed to be easy to use and deploy, making it ideal for scenarios where a simple, multi-tenant queue is needed without the overhead of a full-fledged message broker.

## High-Level Design

### Architecture Diagram

The following diagram illustrates the high-level architecture of the Simple Queue Service:

```mermaid
graph TD
    A[Client] --> B{Simple Queue Service API};
    B --> C[MessageController];
    C --> D[MessageService];
    D --> E[Spring Data MongoDB];
    E --> F[(MongoDB)];

    subgraph Spring Boot Application
        C
        D
        E
    end
```

### Workflow Flowchart

The following flowchart details the overall operational flow of the Simple Queue Service, from client interaction to data persistence, highlighting the three main operations: push, pop, and view.

```mermaid
graph TD
    subgraph Client Interaction
        A[Client]
    end

    subgraph Simple Queue Service
        B(MessageController)
        C(MessageService)
        D(MongoDB)
    end

    A -- HTTP Request --> B
    B -- Calls Service --> C
    C -- Interacts with DB --> D

    subgraph Push Operation
        B_push(POST /queue/push) --> C_push[MessageService.push]
        C_push -- Saves Message --> D_push(MongoDB Collection)
    end

    subgraph Pop Operation
        B_pop(GET /queue/pop) --> C_pop[MessageService.pop]
        C_pop -- Finds & Modifies Oldest Message --> D_pop(MongoDB Collection)
    end

    subgraph View Operation
        B_view(GET /queue/view) --> C_view[MessageService.view]
        C_view -- Retrieves All Messages --> D_view(MongoDB Collection)
    end

    B_push -- Response 200 OK --> A
    B_pop -- Response 200 OK / 404 Not Found --> A
    B_view -- Response 200 OK --> A
```

## Features

*   **Multi-tenancy:** Supports multiple consumer groups, with each group having its own dedicated queue (MongoDB collection).
*   **RESTful API:** Provides a simple and intuitive API for interacting with the queue.
*   **Message Persistence:** Uses MongoDB to store messages, ensuring data durability.
*   **At-Least-Once Delivery:** The `pop` operation marks messages as processed, which is a step towards ensuring at-least-once delivery.
*   **API Documentation:** Integrated with SpringDoc to provide OpenAPI documentation.

## Message Retention

This service implements a Time-To-Live (TTL) policy for messages to prevent the database from growing indefinitely. Messages are automatically deleted from the queue after a configurable period.

- **Default Retention Period:** By default, messages are retained for **10 minutes**.
- **Configuration:** This duration can be configured in the `application.properties` file by setting the `message.expiry.minutes` property.

## Technologies Used

*   **Java 17**
*   **Spring Boot 3.2.5**
*   **Spring Data MongoDB:** For database interaction.
*   **Spring Web:** For creating the RESTful API.
*   **Spring Security:** For securing the application.
*   **Lombok:** To reduce boilerplate code.
*   **Maven:** For project build and dependency management.
*   **SpringDoc (OpenAPI 3):** For API documentation.

## API Endpoints

All endpoints are relative to the base path `/queue`.

### Push a Message

*   **Method:** `POST`
*   **URL:** `/queue/push`
*   **Headers:**
    *   `consumerGroup`: The name of the consumer group (e.g., `my-app-queue`).
*   **Request Body:**
    ```json
    {
        "key": "value"
    }
    ```
*   **Response:**
    *   **200 OK:**
        ```json
        {
            "id": "632c9e6a5b7d8e1e3e8e1a1a",
            "content": "{\"key\":\"value\"}",
            "processed": false,
            "createdAt": "2025-09-22T14:30:02.123Z"
        }
        ```

### Pop a Message

*   **Method:** `GET`
*   **URL:** `/queue/pop`
*   **Headers:**
    *   `consumerGroup`: The name of the consumer group.
*   **Response:**
    *   **200 OK:** Returns the oldest unprocessed message.
        ```json
        {
            "id": "632c9e6a5b7d8e1e3e8e1a1a",
            "content": "{\"key\":\"value\"}",
            "processed": true,
            "createdAt": "2025-09-22T14:30:02.123Z"
        }
        ```
    *   **404 Not Found:** If the queue is empty.

### View All Messages

*   **Method:** `GET`
*   **URL:** `/queue/view`
*   **Headers:**
    *   `consumerGroup`: The name of the consumer group.
*   **Response:**
    *   **200 OK:** Returns a list of all messages in the queue.
        ```json
        [
            {
                "id": "632c9e6a5b7d8e1e3e8e1a1a",
                "content": "{\"key\":\"value\"}",
                "processed": true,
                "createdAt": "2025-09-22T14:30:02.123Z"
            }
        ]
        ```

## Low-Level Design

### Class Design

*   **`Message.java`**: This is the model class representing a message in the queue.
    *   `id`: The unique identifier for the message (auto-generated by MongoDB).
    *   `content`: The content of the message (stored as a string).
    *   `createdAt`: Timestamp when the message was created.
    *   `processed`: A boolean flag to indicate if the message has been popped from the queue.

*   **`MessageController.java`**: This class is responsible for handling the incoming HTTP requests.
    *   `POST /queue/push`: Pushes a new message to the queue for a specific `consumerGroup`.
    *   `GET /queue/pop`: Pops the oldest message from the queue for a specific `consumerGroup`.
    *   `GET /queue/view`: Views all messages in the queue for a specific `consumerGroup`.

*   **`MessageService.java`**: This class contains the business logic for the queue operations.
    *   `push(consumerGroup, content)`: Saves a new message to the specified `consumerGroup` collection in MongoDB.
    *   `pop(consumerGroup)`: Finds the oldest unprocessed message, marks it as processed, and returns it. This is an atomic operation.
    *   `view(consumerGroup)`: Retrieves all messages from the specified `consumerGroup` collection.

*   **`MongoTTLConfig.java`**: This class configures the Time-To-Live (TTL) index on the `createdAt` field for all message collections. This ensures that messages are automatically deleted after a configured period.

*   **`SecurityConfig.java`**: This class is responsible for the security configuration of the application. It can be used to add authentication and authorization mechanisms.

*   **`GlobalExceptionHandler.java`**: This class handles exceptions thrown by the application and returns appropriate HTTP responses.

### Data Model

The `Message` documents are stored in MongoDB collections. The name of the collection is the same as the `consumerGroup`. Each document has the following structure:

```json
{
    "_id": "632c9e6a5b7d8e1e3e8e1a1a",
    "content": "{\"key\":\"value\"}",
    "createdAt": "2025-09-22T14:30:02.123Z",
    "processed": false,
    "_class": "com.example.simplequeueservice.model.Message"
}
```


## Sequence Diagrams

### Push Operation

```mermaid
sequenceDiagram
    participant Client
    participant MessageController
    participant MessageService
    participant MongoTemplate
    participant MongoDB

    Client->>+MessageController: POST /queue/push (consumerGroup, message)
    MessageController->>+MessageService: push(consumerGroup, message)
    MessageService->>+MongoTemplate: save(message, consumerGroup)
    MongoTemplate->>+MongoDB: insert(message)
    MongoDB-->>-MongoTemplate: savedMessage
    MongoTemplate-->>-MessageService: savedMessage
    MessageService-->>-MessageController: savedMessage
    MessageController-->>-Client: 200 OK (savedMessage)
```

### Pop Operation

```mermaid
sequenceDiagram
    participant Client
    participant MessageController
    participant MessageService
    participant MongoTemplate
    participant MongoDB

    Client->>MessageController: GET /queue/pop (consumerGroup)
    activate MessageController
    MessageController->>MessageService: pop(consumerGroup)
    activate MessageService
    MessageService->>MongoTemplate: findAndModify(query, update, options, Message.class, consumerGroup)
    activate MongoTemplate
    MongoTemplate->>MongoDB: findAndModify(query, update)
    activate MongoDB
    MongoDB-->>MongoTemplate: modifiedMessage
    deactivate MongoDB
    MongoTemplate-->>MessageService: Optional<modifiedMessage>
    deactivate MongoTemplate
    MessageService-->>MessageController: Optional<modifiedMessage>
    deactivate MessageService
    alt Message Found
        MessageController-->>Client: 200 OK (modifiedMessage)
    else Message Not Found
        MessageController-->>Client: 404 Not Found
    end
    deactivate MessageController
```

### Error Handling

*   **`GlobalExceptionHandler.java`** uses `@RestControllerAdvice` to handle exceptions globally.
*   For example, it can handle `MethodArgumentNotValidException` for validation failures and return a `400 Bad Request` response with a clear error message.
*   Custom exceptions can be created and handled here to provide specific error responses for different scenarios.

### Configuration

The following properties can be configured in `application.properties`:

*   `spring.data.mongodb.uri`: The connection URI for the MongoDB instance.
*   `message.expiry.minutes`: The retention period for messages in minutes. Default is 10.
*   `server.port`: The port on which the application will run. Default is 8080.

## Getting Started

### Prerequisites

*   Java 17 or later
*   Maven 3.2+
*   MongoDB instance running

### Configuration

1.  Clone the repository:
    ```bash
    git clone https://github.com/Alak-Das/simple-queue-service.git
    ```
2.  Navigate to the project directory:
    ```bash
    cd simple-queue-service
    ```
3.  Configure the MongoDB connection in `src/main/resources/application.properties`:
    ```properties
    spring.data.mongodb.uri=mongodb://localhost:27017/mydatabase
    ```

### Build and Run

1.  Build the project using Maven:
    ```bash
    mvn clean install
    ```
2.  Run the application:
    ```bash
    java -jar target/simple-queue-service-0.0.1-SNAPSHOT.jar
    ```

The application will be available at `http://localhost:8080`.

### API Documentation

Once the application is running, the OpenAPI documentation can be accessed at:
[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
