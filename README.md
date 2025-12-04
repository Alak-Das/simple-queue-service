# Simple Queue Service

A lightweight, high-performance message queue service built with Spring Boot, providing RESTful APIs for asynchronous message processing with support for consumer groups, Redis caching, and MongoDB persistence.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-Cache-red.svg)](https://redis.io/)
[![MongoDB](https://img.shields.io/badge/MongoDB-Database-green.svg)](https://www.mongodb.com/)

## Table of Contents
1.  [Project Overview](#1-project-overview)
2.  [Key Features](#2-key-features)
3.  [Architecture Overview](#3-architecture-overview)
4.  [Technology Stack](#4-technology-stack)
5.  [Getting Started](#5-getting-started)
6.  [API Documentation](#6-api-documentation)
7.  [Design Documents](#7-design-documents)
8.  [Performance Considerations](#8-performance-considerations)
9.  [Security](#9-security)
10. [Troubleshooting](#10-troubleshooting)
11. [Contributing](#11-contributing)
12. [License](#12-license)

## 1. Project Overview

The Simple Queue Service is a production-ready Spring Boot application that implements a distributed message queue system. It enables asynchronous communication between producers and consumers through a RESTful API, with support for multiple isolated consumer groups.

### Use Cases

- **Microservices Communication**: Decouple services with asynchronous messaging
- **Task Queue Management**: Distribute work across multiple consumers
- **Event Processing**: Handle events with guaranteed delivery and processing
- **Load Leveling**: Buffer requests during traffic spikes
- **Background Job Processing**: Queue long-running tasks for asynchronous execution

## 2. Key Features

### Core Functionality
- ✅ **Message Pushing**: Add messages to consumer-specific queues
- ✅ **Message Popping**: Retrieve and automatically mark messages as consumed
- ✅ **Message Viewing**: Inspect queue contents with filtering options
- ✅ **Consumer Groups**: Isolated message streams for multi-tenant scenarios

### Performance & Reliability
- ⚡ **Redis Caching**: Sub-millisecond message retrieval for hot data
- 💾 **MongoDB Persistence**: Durable storage with automatic TTL cleanup
- 🔄 **Asynchronous Processing**: Non-blocking database operations
- 📊 **Scalable Design**: Horizontal scaling support via external data stores

### Operations & Security
- 🔐 **Role-Based Access Control**: User and Admin roles with HTTP Basic Auth
- 📝 **API Documentation**: Interactive Swagger UI
- 🐳 **Docker Support**: Containerized deployment ready
- 📈 **Production Ready**: Exception handling, logging, and monitoring hooks

## 3. Architecture Overview

### System Architecture

```mermaid
graph TB
    subgraph "Client Applications"
        P[Producer]
        C[Consumer]
    end
    
    subgraph "Simple Queue Service"
        API[REST API Layer<br/>MessageController]
        SVC[Service Layer<br/>Push/Pop/View Services]
        CACHE[Cache Service<br/>Redis Abstraction]
    end
    
    subgraph "Data Layer"
        R[(Redis<br/>In-Memory Cache)]
        M[(MongoDB<br/>Persistent Storage)]
    end
    
    P -->|POST /queue/push| API
    C -->|GET /queue/pop| API
    C -->|GET /queue/view| API
    
    API --> SVC
    SVC --> CACHE
    SVC -.->|Async Write| M
    CACHE --> R
    SVC --> M
    
    style API fill:#e1f5ff
    style SVC fill:#fff4e1
    style CACHE fill:#ffe1e1
    style R fill:#ffcccc
    style M fill:#ccffcc
```

### Data Flow

#### Push Operation
```
Client → Controller → Service → [Redis Cache + MongoDB (Async)] → Response
```

#### Pop Operation (Cache Hit)
```
Client → Controller → Service → Redis Cache → Response + MongoDB Update (Async)
```

#### Pop Operation (Cache Miss)
```
Client → Controller → Service → MongoDB (Find & Update) → Response
```

## 4. Technology Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Framework** | Spring Boot 3.x (Java 21) | Core application framework |
| **Web** | Spring Web | RESTful API endpoints |
| **Cache** | Redis + Spring Data Redis | High-speed message caching |
| **Database** | MongoDB + Spring Data MongoDB | Persistent message storage |
| **Security** | Spring Security | Authentication & authorization |
| **Async** | Spring Async | Non-blocking operations |
| **Documentation** | Springdoc OpenAPI (Swagger) | API documentation |
| **Utilities** | Lombok, Apache Commons Lang3 | Code simplification |
| **Container** | Docker | Deployment packaging |

## 5. Getting Started

### Prerequisites

- **Java 21** or higher ([Download](https://adoptium.net/))
- **Maven 3.6+** ([Download](https://maven.apache.org/download.cgi))
- **Redis 6.0+** ([Installation Guide](https://redis.io/docs/getting-started/installation/))
- **MongoDB 5.0+** ([Installation Guide](https://www.mongodb.com/docs/manual/installation/))

### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd simple-queue-service
   ```

2. **Configure application properties**

   Edit `src/main/resources/application.properties`:
   ```properties
   # Redis Configuration
   spring.data.redis.host=localhost
   spring.data.redis.port=6379
   cache.ttl.minutes=60
   
   # MongoDB Configuration
   spring.data.mongodb.host=localhost
   spring.data.mongodb.port=27017
   spring.data.mongodb.database=queuedb
   persistence.duration.minutes=1440
   
   # Security
   spring.security.user.name=user
   spring.security.user.password=password
   admin.username=admin
   admin.password=adminpassword
   
   # API Configuration
   no.of.message.allowed.to.fetch=100
   ```

3. **Build the application**
   ```bash
   mvn clean install
   ```

4. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

   Or run the JAR directly:
   ```bash
   java -jar target/simple-queue-service-1.0.0.jar
   ```

### Docker Deployment

1. **Build Docker image**
   ```bash
   docker build -t simple-queue-service:latest .
   ```

2. **Run with Docker Compose** (create `docker-compose.yml`):
   ```yaml
   version: '3.8'
   services:
     redis:
       image: redis:7-alpine
       ports:
         - "6379:6379"
     
     mongodb:
       image: mongo:6
       ports:
         - "27017:27017"
       environment:
         MONGO_INITDB_DATABASE: queuedb
     
     queue-service:
       image: simple-queue-service:latest
       ports:
         - "8080:8080"
       depends_on:
         - redis
         - mongodb
       environment:
         SPRING_DATA_REDIS_HOST: redis
         SPRING_DATA_MONGODB_HOST: mongodb
   ```

3. **Start services**
   ```bash
   docker-compose up -d
   ```

### Verify Installation

Access the Swagger UI at: `http://localhost:8080/swagger-ui.html`

## 6. API Documentation

### Base URL
```
http://localhost:8080/queue
```

### Authentication

All endpoints require HTTP Basic Authentication:

| Role | Username | Password | Permissions |
|------|----------|----------|-------------|
| User | `user` | `password` | Push, Pop |
| Admin | `admin` | `adminpassword` | Push, Pop, View |

### Endpoints

#### 1. Push Message

Add a new message to a consumer group's queue.

```http
POST /queue/push
Content-Type: text/plain
consumerGroup: my-consumer-group
Authorization: Basic dXNlcjpwYXNzd29yZA==

Hello, World!
```

**Response (200 OK):**
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "content": "Hello, World!",
  "createdAt": "2024-12-05T10:30:00"
}
```

#### 2. Pop Message

Retrieve and consume the oldest message from a queue.

```http
GET /queue/pop
consumerGroup: my-consumer-group
Authorization: Basic dXNlcjpwYXNzd29yZA==
```

**Response (200 OK):**
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "content": "Hello, World!",
  "createdAt": "2024-12-05T10:30:00"
}
```

**Response (404 Not Found):** No messages available

#### 3. View Messages (Admin Only)

Inspect messages in a queue without consuming them.

```http
GET /queue/view
consumerGroup: my-consumer-group
messageCount: 10
consumed: false
Authorization: Basic YWRtaW46YWRtaW5wYXNzd29yZA==
```

**Response (200 OK):**
```json
[
  {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "content": "Hello, World!",
    "consumerGroup": "my-consumer-group",
    "createdAt": "2024-12-05T10:30:00",
    "consumed": false
  }
]
```

### Error Responses

All errors follow this structure:
```json
{
  "timestamp": "2024-12-05T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Consumer group header is required",
  "path": "/queue/push"
}
```

## 7. Design Documents

### High-Level Design (HLD)

#### System Components

1. **REST API Layer**: Handles HTTP requests/responses, validation
2. **Service Layer**: Business logic for push, pop, view operations
3. **Cache Layer**: Redis-based fast access to recent messages
4. **Persistence Layer**: MongoDB for durable storage with TTL
5. **Security Layer**: Authentication and authorization
6. **Async Layer**: Thread pool for non-blocking operations

### Low-Level Design (LLD)

#### Class Diagram

```mermaid
classDiagram
    class MessageController {
        +MessageResponse push(String, String)
        +ResponseEntity~MessageResponse~ pop(String)
        +ResponseEntity~List~ view(String, int, String)
    }
    
    class PushMessageService {
        -MongoTemplate mongoTemplate
        -CacheService cacheService
        -Executor taskExecutor
        +Message push(Message)
        -void createTTLIndex(Message)
    }
    
    class PopMessageService {
        -MongoTemplate mongoTemplate
        -CacheService cacheService
        -Executor taskExecutor
        +Optional~Message~ pop(String)
        -void updateMessageInMongo(String, String)
    }
    
    class ViewMessageService {
        -MongoTemplate mongoTemplate
        -CacheService cacheService
        +List~Message~ view(String, int, String)
    }
    
    class CacheService {
        -RedisTemplate redisTemplate
        +void addMessage(Message)
        +Message popMessage(String)
        +List~Message~ viewMessages(String)
    }
    
    class Message {
        +String id
        +String content
        +String consumerGroup
        +Date createdAt
        +boolean consumed
    }
    
    MessageController --> PushMessageService
    MessageController --> PopMessageService
    MessageController --> ViewMessageService
    PushMessageService --> CacheService
    PopMessageService --> CacheService
    ViewMessageService --> CacheService
```

### Database Design

#### MongoDB Schema

**Collection Naming**: `{consumerGroup}` (e.g., `payments-queue`, `notifications-queue`)

**Document Structure**:
```json
{
  "_id": "uuid-string",
  "content": "message payload",
  "consumerGroup": "group-name",
  "createdAt": ISODate("2024-12-05T10:30:00Z"),
  "consumed": false
}
```

**Indexes**:
- `_id`: Primary key (unique)
- `createdAt`: TTL index (auto-deletion after configured minutes)
- `consumed`: Regular index (filter unconsumed messages)

#### Redis Data Structure

**Key Pattern**: `consumerGroupMessages:{consumerGroup}`

**Data Type**: List (FIFO queue)

**Operations**:
- Push: `LPUSH` (left push - add to head)
- Pop: `RPOP` (right pop - remove from tail)
- View: `LRANGE` (range query)

**TTL**: Configured via `cache.ttl.minutes`

## 8. Performance Considerations

### Caching Strategy

- **Write-Through Cache**: Messages written to Redis immediately, MongoDB asynchronously
- **Cache First Read**: Pop operations check Redis before MongoDB
- **TTL Management**: Automatic expiration prevents memory bloat

### Optimization Tips

1. **Tune Thread Pool**: Adjust `AsyncConfig` executor settings based on load
   ```java
   executor.setCorePoolSize(10);
   executor.setMaxPoolSize(50);
   executor.setQueueCapacity(500);
   ```

2. **Redis Connection Pooling**: Configure `RedisTemplate` with connection pool
3. **MongoDB Indexes**: Ensure indexes exist on high-query fields
4. **Message Size**: Keep message payloads under 1MB for optimal performance
5. **Consumer Groups**: Use separate groups to parallelize processing

### Scalability

- **Horizontal Scaling**: Run multiple instances (stateless design)
- **Redis Clustering**: Use Redis Cluster for distributed caching
- **MongoDB Sharding**: Shard by `consumerGroup` for large-scale deployments

## 9. Security

### Authentication Methods

- **HTTP Basic Auth**: Default for quick setup
- **Recommended for Production**:
    - JWT tokens
    - OAuth 2.0
    - API Keys with rate limiting

### Best Practices

1. **Change Default Credentials**: Update `application.properties` before deployment
2. **Use HTTPS**: Enable SSL/TLS in production
3. **Network Isolation**: Deploy Redis/MongoDB in private networks
4. **Role Separation**: Create custom roles beyond User/Admin
5. **Audit Logging**: Enable Spring Security audit logs

### Security Configuration Example

```java
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/queue/push", "/queue/pop").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/queue/view").hasRole("ADMIN")
            )
            .httpBasic(Customizer.withDefaults())
            .build();
    }
}
```

## 10. Troubleshooting

### Common Issues

#### 1. Connection Refused (Redis/MongoDB)

**Problem**: Service can't connect to Redis or MongoDB

**Solution**:
```bash
# Check Redis is running
redis-cli ping  # Should return PONG

# Check MongoDB is running
mongosh --eval "db.adminCommand('ping')"

# Verify connection settings in application.properties
```

#### 2. Messages Not Persisting

**Problem**: Messages disappear unexpectedly

**Solution**:
- Check TTL settings: `persistence.duration.minutes`
- Verify MongoDB TTL indexes: `db.{collection}.getIndexes()`
- Check async executor isn't overloaded

#### 3. Slow Pop Operations

**Problem**: Pop requests taking too long

**Solution**:
- Monitor Redis cache hit rate
- Check MongoDB indexes on `consumed` field
- Review network latency between service and databases

#### 4. Authentication Failures

**Problem**: 401 Unauthorized errors

**Solution**:
```bash
# Test with curl
curl -u user:password http://localhost:8080/queue/pop \
  -H "consumerGroup: test-group"

# Verify credentials in application.properties
```

### Health Checks

Add Spring Boot Actuator for monitoring:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Access health endpoints:
- `GET /actuator/health` - Overall health
- `GET /actuator/metrics` - Application metrics

## 11. Contributing

We welcome contributions! Please follow these guidelines:

### Development Setup

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Make your changes
4. Run tests: `mvn test`
5. Commit: `git commit -m 'Add amazing feature'`
6. Push: `git push origin feature/amazing-feature`
7. Open a Pull Request

### Code Standards

- Follow Java naming conventions
- Add JavaDoc for public methods
- Write unit tests for new features
- Maintain >80% code coverage
- Use Lombok annotations to reduce boilerplate

### Reporting Issues

Please include:
- Clear description of the issue
- Steps to reproduce
- Expected vs actual behavior
- Environment details (Java version, OS, etc.)

## 12. License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Additional Resources

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Redis Documentation](https://redis.io/docs/)
- [MongoDB Documentation](https://www.mongodb.com/docs/)
- [REST API Best Practices](https://restfulapi.net/)

## Support

For questions or support:
- 📧 Email: [alakdas.mail@gmail.com](mailto:alakdas.mail@gmail.com)
- 🐛 Issues: [GitHub Issues](https://github.com/Alak-Das/simple-queue-service/issues)

---

**Built with ❤️ using Spring Boot**