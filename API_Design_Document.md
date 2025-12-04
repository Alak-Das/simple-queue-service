# API Design Document

## 1. Introduction

This API Design Document (ADD) specifies the RESTful API endpoints for the Simple Queue Service (SQS). It details the available operations, request/response formats, authentication requirements, and error handling for interacting with the message queue system.

## 2. Base URL

All API endpoints are prefixed with: `/queue`

## 3. Authentication

The SQS API requires HTTP Basic Authentication for all protected endpoints. User credentials (username and password) are configured in the `application.properties` file or via environment variables.

### Configured Users:

*   **USER Role:**
    *   Username: `security.user.username` (default: `user`)
    *   Password: `security.user.password` (default: `password`)
    *   Roles: `USER`
*   **ADMIN Role:**
    *   Username: `security.admin.username` (default: `admin`)
    *   Password: `security.admin.password` (default: `adminpassword`)
    *   Roles: `ADMIN`, `USER`

## 4. API Endpoints

### 4.1 Push a Message

*   **Description:** Pushes a new message to the specified consumer group.
*   **HTTP Method:** `POST`
*   **Endpoint:** `/queue/push`
*   **Roles Required:** `USER`, `ADMIN`

*   **Request Headers:**
    *   `consumerGroup`: `String` (Required)
        *   **Description:** The unique identifier for the consumer group to which the message will be pushed.
        *   **Example:** `my-app-queue`

*   **Request Body:**
    *   **Content Type:** `application/json` or `text/plain`
    *   **Schema:** `String`
        *   **Description:** The actual content of the message. This can be any string, typically a JSON payload.
        *   **Example:**
            ```json
            {
                "orderId": "12345",
                "item": "Laptop",
                "quantity": 1
            }
            ```

*   **Success Response (200 OK):**
    *   **Content Type:** `application/json`
    *   **Schema:** `MessageResponse` object
        ```json
        {
            "id": "e4f8a9d7-b3c6-4e1d-8f2a-0b9c7d6a5e4f",
            "content": "{\"orderId\":\"12345\",\"item\":\"Laptop\",\"quantity\":1}",
            "createdAt": "2025-05-12T12:00:00.123456789"
        }
        ```
    *   **Fields:**
        *   `id`: `String` - The unique identifier assigned to the pushed message.
        *   `content`: `String` - The content of the message as it was pushed.
        *   `createdAt`: `LocalDateTime` - The timestamp when the message was created in the queue.

*   **Error Responses:**
    *   `400 Bad Request`: (e.g., Missing `consumerGroup` header, invalid request body format, or empty `content`).
        ```json
        {
            "timestamp": "2025-05-12T12:00:00.123456789",
            "status": 400,
            "error": "Bad Request",
            "message": "Message content is mandatory",
            "path": "/queue/push"
        }
        ```
    *   `401 Unauthorized`: (e.g., Missing or invalid HTTP Basic credentials).
    *   `403 Forbidden`: (e.g., Authenticated user does not have `USER` or `ADMIN` role).
    *   `500 Internal Server Error`: (e.g., An unexpected server error occurred).

### 4.2 Pop a Message

*   **Description:** Retrieves and marks as consumed the oldest available message from the specified consumer group. The message is removed from the active queue for subsequent `pop` operations.
*   **HTTP Method:** `GET`
*   **Endpoint:** `/queue/pop`
*   **Roles Required:** `USER`, `ADMIN`

*   **Request Headers:**
    *   `consumerGroup`: `String` (Required)
        *   **Description:** The unique identifier for the consumer group from which to pop the message.
        *   **Example:** `my-app-queue`

*   **Request Body:** None

*   **Success Response (200 OK):**
    *   **Content Type:** `application/json`
    *   **Schema:** `MessageResponse` object
        ```json
        {
            "id": "e4f8a9d7-b3c6-4e1d-8f2a-0b9c7d6a5e4f",
            "content": "{\"orderId\":\"12345\",\"item\":\"Laptop\",\"quantity\":1}",
            "createdAt": "2025-05-12T11:59:00.123456789"
        }
        ```
    *   **Fields:** Same as `Push a Message` success response.

*   **Error Responses:**
    *   `404 Not Found`: (e.g., No unprocessed messages found in the specified consumer group).
        ```json
        {
            "timestamp": "2025-05-12T12:05:00.123456789",
            "status": 404,
            "error": "Not Found",
            "message": "No message found to pop for consumer group my-app-queue",
            "path": "/queue/pop"
        }
        ```
    *   `401 Unauthorized`: (e.g., Missing or invalid HTTP Basic credentials).
    *   `403 Forbidden`: (e.g., Authenticated user does not have `USER` or `ADMIN` role).
    *   `500 Internal Server Error`: (e.g., An unexpected server error occurred).

### 4.3 View Messages

*   **Description:** Retrieves a list of messages from the specified consumer group. This operation supports filtering by message consumption status and limiting the number of results.
*   **HTTP Method:** `GET`
*   **Endpoint:** `/queue/view`
*   **Roles Required:** `ADMIN`

*   **Request Headers:**
    *   `consumerGroup`: `String` (Required)
        *   **Description:** The unique identifier for the consumer group whose messages will be viewed.
        *   **Example:** `my-app-queue`
    *   `messageCount`: `int` (Required)
        *   **Description:** The maximum number of messages to retrieve. This value is constrained by `no.of.message.allowed.to.fetch` in `application.properties` (default: 50).
        *   **Example:** `10`
    *   `consumed`: `String` (Optional)
        *   **Description:** Filters messages based on their consumption status.
        *   **Accepted Values:**
            *   `"yes"`: Returns only messages that have been consumed (processed).
            *   `"no"`: Returns only messages that have not yet been consumed (unprocessed).
        *   **Default:** If not provided, returns all messages (both consumed and unconsumed).

*   **Request Body:** None

*   **Success Response (200 OK):**
    *   **Content Type:** `application/json`
    *   **Schema:** `List<Message>` objects. Note that the full `Message` entity (including `consumed` and `consumerGroup`) is returned, unlike `MessageResponse`.
        ```json
        [
            {
                "id": "e4f8a9d7-b3c6-4e1d-8f2a-0b9c7d6a5e4f",
                "content": "{\"orderId\":\"12345\",\"item\":\"Laptop\",\"quantity\":1}",
                "consumerGroup": "my-app-queue",
                "createdAt": "2025-05-12T11:59:00.123Z",
                "consumed": true
            },
            {
                "id": "f5g9b0e8-c4d7-4f2a-9e3b-1c0d8e7b6f5a",
                "content": "{\"productId\":\"XYZ\",\"action\":\"viewed\"}",
                "consumerGroup": "my-app-queue",
                "createdAt": "2025-05-12T12:01:00.456Z",
                "consumed": false
            }
        ]
        ```
    *   **Fields:**
        *   `id`: `String` - The unique identifier of the message.
        *   `content`: `String` - The content of the message.
        *   `consumerGroup`: `String` - The consumer group the message belongs to.
        *   `createdAt`: `Date` - The timestamp when the message was created.
        *   `consumed`: `Boolean` - Indicates if the message has been processed.

*   **Error Responses:**
    *   `400 Bad Request`: (e.g., Missing `consumerGroup` or `messageCount` headers, `messageCount` out of allowed range, invalid `consumed` value).
        ```json
        {
            "timestamp": "2025-05-12T12:10:00.123456789",
            "status": 400,
            "error": "Bad Request",
            "message": "Message Count should be 1 to 50.",
            "path": "/queue/view"
        }
        ```
    *   `401 Unauthorized`: (e.g., Missing or invalid HTTP Basic credentials).
    *   `403 Forbidden`: (e.g., Authenticated user does not have `ADMIN` role).
    *   `500 Internal Server Error`: (e.g., An unexpected server error occurred).

## 5. Error Handling Standard

All error responses from the API will adhere to the `ErrorResponse` JSON structure:

```json
{
    "timestamp": "2025-05-12T12:00:00.123456789",
    "status": <HTTP Status Code>,
    "error": "<HTTP Status Reason Phrase>",
    "message": "<Detailed error message>",
    "path": "<Request URI>"
}
```

## 6. OpenAPI (Swagger) Documentation

The API is self-documented using SpringDoc OpenAPI 3. Once the application is running, the interactive API documentation can be accessed at: `http://localhost:8080/swagger-ui.html`.
