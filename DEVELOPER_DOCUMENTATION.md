# Semantic Kernel Spring Symphony - Developer Documentation

## Table of Contents
1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Getting Started](#getting-started)
4. [Core Components](#core-components)
5. [Workflow and Execution Flow](#workflow-and-execution-flow)
6. [Configuration](#configuration)
7. [Key Classes and Interfaces](#key-classes-and-interfaces)
8. [Step Types](#step-types)
9. [Plugin System](#plugin-system)
10. [Development Guidelines](#development-guidelines)
11. [API Reference](#api-reference)

---

## Project Overview

**Semantic Kernel Spring Symphony** is an intelligent chat agentic framework that integrates Microsoft's Semantic Kernel with Spring Boot. It provides a comprehensive solution for building sophisticated AI-driven applications with support for multiple data sources, knowledge bases, and execution workflows.

### Key Features
- **Agentic AI Framework**: Leverages Microsoft Semantic Kernel for AI capabilities
- **Multi-Step Workflows**: Execute complex workflows with multiple step types (SQL, REST, GraphQL, etc.)
- **Knowledge Graph Integration**: Build and query knowledge graphs
- **Vector Search Support**: Azure AI Search integration for semantic search
- **Plugin System**: Extensible plugin architecture
- **Session Management**: Track and manage user sessions
- **Stream Processing**: Reactive streams for real-time response handling
- **Multiple AI Integrations**: OpenAI, Azure OpenAI, and Semantic Kernel support

### Technology Stack
- **Java**: JDK 17+
- **Framework**: Spring Boot 3.4.2
- **Core Dependencies**:
  - Microsoft Semantic Kernel 1.4.4-RC2
  - Spring AI 1.1.2
  - Azure AI Services
  - Jackson for JSON processing
  - Reactor for reactive programming
  - Redis for caching
  - Apache POI for document processing
  - Apache PDFBox for PDF handling

---

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Client Application                        │
├─────────────────────────────────────────────────────────────┤
│                      Chat Request API                        │
├─────────────────────────────────────────────────────────────┤
│                   Agent (Request Router)                     │
├──────────────────────┬──────────────────────────────────────┤
│  KnowledgeGraph      │   ExecutionContext Manager            │
│  Builder             │                                       │
├──────────────────────┼──────────────────────────────────────┤
│         Symphony (Orchestrator)                             │
├──────────────────────────────────────────────────────────────┤
│  ┌──────────┬──────────┬──────────┬──────────┬────────────┐ │
│  │ REST     │ SQL      │GraphQL   │ Plugin   │ Velocity   │ │
│  │ Step     │ Step     │ Step     │ Step     │ Step       │ │
│  └──────────┴──────────┴──────────┴──────────┴────────────┘ │
├──────────────────────────────────────────────────────────────┤
│                   AI Client (OpenAI/Azure)                   │
├──────────────────────────────────────────────────────────────┤
│           Knowledge Base & Vector Search Services            │
└─────────────────────────────────────────────────────────────┘
```

### Layered Architecture

1. **Presentation Layer**: REST APIs and Chat interfaces
2. **Service Layer**: Agent, KnowledgeGraphBuilder, Symphony orchestrator
3. **Execution Layer**: Various Step implementations
4. **Integration Layer**: AI services, database connectors, external APIs
5. **Data Layer**: Knowledge base, session storage, Redis cache

---

## Getting Started

### Prerequisites
- JDK 17 or higher
- Maven 3.8.1 or higher
- Azure credentials (if using Azure services)
- OpenAI API keys (if using OpenAI services)

### Build and Install

```bash
# Clone the repository
git clone https://github.com/cibinmathewjose/semanticsymphony.git
cd semanticsymphony

# Build with Maven
mvn clean install

# Run tests
mvn test

# Build JAR
mvn package
```

### Maven Dependency

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>org.symphonykernel</groupId>
    <artifactId>semantickernel-spring-symphony</artifactId>
    <version>0.4.7-SNAPSHOT</version>
</dependency>
```

Or use GitHub Packages:

```xml
<repository>
    <id>github</id>
    <name>GitHub Packages</name>
    <url>https://maven.pkg.github.com/cibinmathewjose/semanticsymphony</url>
</repository>
```

---

## Core Components

### 1. ChatRequest
**Package**: `org.symphonykernel`

Represents an incoming chat request from a client.

**Key Properties**:
- `key`: Unique request identifier
- `query`: User's question or command
- `user`: User identifier
- `session`: Session identifier
- `conversationId`: Conversation identifier
- `payload`: Additional data payload
- `contextInfo`: Map of context-specific information
- `httpHeaderProvider`: HTTP headers provider

**Usage**:
```java
ChatRequest request = new ChatRequest();
request.setQuery("What is the weather?");
request.setUser("user123");
request.setSession("session456");
```

### 2. ChatResponse
**Package**: `org.symphonykernel`

Represents the response generated by the framework.

**Key Properties**:
- `requestId`: Identifier of the original request
- `message`: Response message
- `messageType`: Type of message (text, structured, etc.)
- `statusCode`: HTTP status code
- `node`: ArrayNode containing response data

**Usage**:
```java
ChatResponse response = new ChatResponse("Generated response");
response.setRequestId("req123");
response.setStatusCode("200");
```

### 3. ExecutionContext
**Package**: `org.symphonykernel`

Encapsulates the complete execution context for processing.

**Key Properties**:
- `header`: HTTP header provider
- `variables`: JsonNode containing execution variables
- `kb`: Associated Knowledge base
- `name`: Execution context name
- `modelName`: AI model name to use
- `usersQuery`: Original user query
- `convert`: Flag for JSON conversion
- `chatHistory`: Chat history for multi-turn conversations
- `resolvedValues`: Map of resolved values during execution

**Key Methods**:
```java
public JsonNode getVariables()
public void setVariables(JsonNode variables)
public Knowledge getKnowledge()
public void setKnowledge(Knowledge kb)
public Map<String, JsonNode> getResolvedValues()
```

### 4. Knowledge
**Package**: `org.symphonykernel`

Represents a knowledge entity in the knowledge base.

**Key Properties**:
- `name`: Name of the knowledge
- `description`: Human-readable description
- `type`: QueryType (SQL, REST, GraphQL, etc.)
- `params`: Parameter definitions
- `data`: Knowledge configuration or workflow definition
- `card`: Visual card representation
- `url`: Associated URL
- `tools`: Associated tools
- `systemPrompt`: System prompt for AI processing

### 5. Agent
**Package**: `org.symphonykernel.ai`

Main service for processing chat requests.

**Key Methods**:
```java
public ChatResponse process(ChatRequest request)
public Flux<String> streamProcess(ChatRequest request)
public ChatResponse getAsyncResults(String requestId)
public ChatResponse processFollowUp(String requestId, String query)
```

### 6. KnowledgeGraphBuilder
**Package**: `org.symphonykernel.ai`

Builds execution contexts and manages the knowledge graph.

**Responsibilities**:
- Create execution contexts from chat requests
- Identify intent and parameters
- Build knowledge graphs
- Generate responses

### 7. Symphony (Orchestrator)
**Package**: `org.symphonykernel.steps`

Main orchestrator for executing workflows.

**Key Responsibilities**:
- Parse flow definitions
- Execute flow items in order
- Handle step results and transformations
- Generate final responses

**Key Methods**:
```java
public ChatResponse getResponse(ExecutionContext ctx)
public Flux<String> getResponseStream(ExecutionContext ctx)
public JsonNode executeQueryByName(ExecutionContext context)
```

---

## Workflow and Execution Flow

### Request Processing Flow

```
1. ChatRequest arrives at Agent
2. Agent calls KnowledgeGraphBuilder.prepareContext()
3. ExecutionContext is created with:
   - User query analysis
   - Parameter extraction
   - Knowledge base lookup
4. Symphony orchestrator receives ExecutionContext
5. FlowJson is parsed from Knowledge.data
6. Each FlowItem is processed sequentially:
   - Variable resolution
   - Transformation
   - Step execution
   - Result storage
7. Final response is built from resolved values
8. ChatResponse is returned to client
```

### Flow Execution Details

Each Flow is defined as JSON with the following structure:

```json
{
  "id": "workflow-name",
  "items": [
    {
      "id": "step-1",
      "type": "REST|SQL|GraphQL|Plugin|Velocity",
      "config": { ... step-specific configuration ... }
    }
  ],
  "output": "expression to select final output"
}
```

### Reactive Processing

The framework uses Project Reactor for asynchronous stream processing:

```java
// Stream responses
Flux<String> response = agent.streamProcess(request);
response.subscribe(
    data -> System.out.println("Received: " + data),
    error -> System.err.println("Error: " + error),
    () -> System.out.println("Completed")
);
```

---

## Configuration

### Application Properties

Key configuration properties in `application.properties`:

```properties
# Azure OpenAI Configuration
spring.ai.azure.openai.api-key=${AZURE_OPENAI_API_KEY}
spring.ai.azure.openai.endpoint=${AZURE_OPENAI_ENDPOINT}
spring.ai.azure.openai.chat.options.model=${AZURE_OPENAI_MODEL}

# Azure AI Search Configuration
spring.ai.azure.search.uri=${AZURE_SEARCH_URI}
spring.ai.azure.search.key=${AZURE_SEARCH_KEY}
spring.ai.azure.search.index-name=${AZURE_SEARCH_INDEX}

# Database Configuration
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

# Redis Configuration
spring.redis.host=${REDIS_HOST}
spring.redis.port=${REDIS_PORT}

# SharePoint Configuration (if using)
spring.sharepoint.site-url=${SHAREPOINT_SITE_URL}
spring.sharepoint.tenant-id=${TENANT_ID}
```

### Spring Boot Auto-Configuration

The framework provides auto-configuration through `SymphonyKernelAutoConfiguration`:

**Key Configuration Classes**:
1. `AzureOpenAiConfig`: Azure OpenAI service setup
2. `VelocityEngineConfig`: Velocity template engine
3. `AzureAISearchConnectionProperties`: Vector search configuration
4. `DBConnectionProperties`: Database connections
5. `RedisConnectionProperties`: Cache configuration

---

## Key Classes and Interfaces

### Core Interfaces

#### IStep
Defines the contract for all step implementations.

```java
public interface IStep {
    ChatResponse getResponse(ExecutionContext context);
    Flux<String> getResponseStream(ExecutionContext context);
    JsonNode executeQueryByName(ExecutionContext context);
}
```

#### IAIClient
Interface for AI service clients.

```java
public interface IAIClient {
    // Methods for AI operations
}
```

#### IknowledgeBase
Interface for knowledge base operations.

```java
public interface IknowledgeBase {
    Knowledge GetByName(String name);
    // Other knowledge base operations
}
```

#### IPluginLoader
Interface for dynamic plugin loading.

```java
public interface IPluginLoader {
    // Plugin loading methods
}
```

#### IUserSessionBase
Interface for session management.

```java
public interface IUserSessionBase {
    // Session management methods
}
```

### BaseStep
Abstract base class for all step implementations.

```java
public abstract class BaseStep implements IStep {
    protected ObjectMapper objectMapper;
    protected IknowledgeBase knowledgeBase;
    protected IUserSessionBase sessionBase;
    
    public Flux<String> getResponseStream(ExecutionContext ctx)
    public JsonNode executeQueryByName(ExecutionContext context)
    public void saveStepData(ExecutionContext context, JsonNode data)
}
```

---

## Step Types

The framework supports multiple step types for different operations:

### 1. RESTStep
**Class**: `org.symphonykernel.steps.RESTStep`

Executes REST API calls.

**Configuration**:
```json
{
  "type": "REST",
  "method": "GET|POST|PUT|DELETE",
  "url": "https://api.example.com/endpoint",
  "headers": { ... },
  "body": "request body or template",
  "timeout": 30000
}
```

### 2. SqlStep
**Class**: `org.symphonykernel.steps.SqlStep`

Executes SQL queries.

**Configuration**:
```json
{
  "type": "SQL",
  "dataSource": "primary|secondary",
  "query": "SELECT * FROM table WHERE id = ?",
  "params": ["param1", "param2"]
}
```

### 3. GraphQLStep
**Class**: `org.symphonykernel.steps.GraphQLStep`

Executes GraphQL queries.

**Configuration**:
```json
{
  "type": "GraphQL",
  "endpoint": "https://api.example.com/graphql",
  "query": "{ query definition }",
  "variables": { ... }
}
```

### 4. PluginStep
**Class**: `org.symphonykernel.steps.PluginStep`

Loads and executes plugins.

**Configuration**:
```json
{
  "type": "Plugin",
  "pluginName": "plugin-name",
  "function": "function-name",
  "parameters": { ... }
}
```

### 5. VelocityStep
**Class**: `org.symphonykernel.steps.VelocityStep`

Processes templates using Velocity.

**Configuration**:
```json
{
  "type": "Velocity",
  "template": "Template content with $variables"
}
```

### 6. ToolStep
**Class**: `org.symphonykernel.steps.ToolStep`

Executes semantic kernel tools.

### 7. FileStep
**Class**: `org.symphonykernel.steps.FileStep`

Handles file operations (PDF extraction, document parsing).

---

## Plugin System

### Creating Custom Plugins

Plugins extend the framework's functionality.

**Base Plugin Class**:
```java
package org.symphonykernel.plugins;

public class SamplePlugin {
    // Plugin methods
}
```

**Steps to Create a Plugin**:

1. Create a class in `org.symphonykernel.plugins` package
2. Implement plugin methods
3. Register in plugin configuration
4. Reference in flow definitions

**Example Plugin**:
```java
public class CustomPlugin {
    public String processData(String input) {
        // Custom business logic
        return processedData;
    }
    
    public JsonNode executeQuery(JsonNode params) {
        // Execute complex logic
        return result;
    }
}
```

**Using a Plugin in a Flow**:
```json
{
  "type": "Plugin",
  "pluginName": "CustomPlugin",
  "function": "processData",
  "parameters": {
    "input": "${variables.userInput}"
  }
}
```

---

## Development Guidelines

### Code Style and Standards

1. **Naming Conventions**:
   - Classes: PascalCase (e.g., `SymmetryHandler`)
   - Methods: camelCase (e.g., `processRequest`)
   - Constants: UPPER_SNAKE_CASE (e.g., `MAX_TIMEOUT`)
   - Variables: camelCase (e.g., `executionContext`)

2. **Logging**:
   - Use SLF4J via Logger
   - Log at appropriate levels: ERROR, WARN, INFO, DEBUG
   - Include meaningful context in log messages

   ```java
   private static final Logger logger = LoggerFactory.getLogger(ClassName.class);
   logger.info("Processing request with ID: {}", requestId);
   ```

3. **Exception Handling**:
   - Catch specific exceptions, not generic `Exception`
   - Log exceptions with full stack trace at appropriate level
   - Provide meaningful error messages to clients

4. **Documentation**:
   - Add JavaDoc comments to public classes and methods
   - Include `@author`, `@version`, `@since` tags
   - Document parameters, return values, and thrown exceptions

   ```java
   /**
    * Processes a chat request and generates a response.
    * 
    * @param request the chat request containing the query
    * @return a ChatResponse containing the generated response
    * @throws IllegalArgumentException if request is null
    */
   public ChatResponse process(ChatRequest request) {
       // implementation
   }
   ```

### Implementing a New Step Type

**Steps**:

1. Create a new class extending `BaseStep` in `org.symphonykernel.steps`
2. Implement required methods: `getResponse()`, `getResponseStream()`, `executeQueryByName()`
3. Add Spring `@Service` annotation
4. Inject required dependencies via `@Autowired`
5. Implement step-specific logic
6. Add proper error handling and logging

**Example New Step**:
```java
package org.symphonykernel.steps;

import org.springframework.stereotype.Service;
import org.symphonykernel.ChatResponse;
import org.symphonykernel.ExecutionContext;
import com.fasterxml.jackson.databind.JsonNode;

@Service("CustomStep")
public class CustomStep extends BaseStep {
    
    @Override
    public ChatResponse getResponse(ExecutionContext context) {
        try {
            // Extract configuration from context
            Knowledge kb = context.getKnowledge();
            JsonNode config = context.getVariables();
            
            // Execute custom logic
            ArrayNode results = processCustomLogic(config);
            
            // Return response
            ChatResponse response = new ChatResponse();
            response.setData(results);
            return response;
        } catch (Exception e) {
            logger.error("Error in CustomStep", e);
            return new ChatResponse("Error: " + e.getMessage());
        }
    }
    
    @Override
    public Flux<String> getResponseStream(ExecutionContext context) {
        return Flux.just(getResponse(context).getData().toString());
    }
    
    @Override
    public JsonNode executeQueryByName(ExecutionContext context) {
        return getResponse(context).getData();
    }
    
    private ArrayNode processCustomLogic(JsonNode config) {
        // Implementation
        return objectMapper.createArrayNode();
    }
}
```

### Testing

**Test Structure**:
- Place tests in `src/test/java` mirroring source structure
- Use JUnit 5 and Mockito for mocking
- Follow naming convention: `ClassNameTest`

**Example Test**:
```java
@ExtendWith(SpringExtension.class)
@SpringBootTest
public class AgentTest {
    
    @MockBean
    private KnowledgeGraphBuilder knowledgeGraphBuilder;
    
    @Autowired
    private Agent agent;
    
    @Test
    void testProcessRequest() {
        // Arrange
        ChatRequest request = new ChatRequest();
        request.setQuery("Test query");
        
        ChatResponse expectedResponse = new ChatResponse("Expected response");
        when(knowledgeGraphBuilder.process(request)).thenReturn(expectedResponse);
        
        // Act
        ChatResponse result = agent.process(request);
        
        // Assert
        assertEquals("Expected response", result.getMessage());
    }
}
```

---

## API Reference

### Agent API

#### Process Request (Synchronous)
```java
ChatResponse process(ChatRequest request)
```
**Parameters**:
- `request`: ChatRequest object

**Returns**: ChatResponse object

**Example**:
```java
ChatRequest request = new ChatRequest();
request.setQuery("What are the top products?");
request.setUser("user123");

ChatResponse response = agent.process(request);
System.out.println(response.getMessage());
```

#### Stream Response (Asynchronous)
```java
Flux<String> streamProcess(ChatRequest request)
```
**Parameters**:
- `request`: ChatRequest object

**Returns**: Flux of String (reactive stream)

**Example**:
```java
agent.streamProcess(request)
    .doOnNext(chunk -> System.out.print(chunk))
    .doOnError(error -> System.err.println("Error: " + error))
    .doOnComplete(() -> System.out.println("Done"))
    .subscribe();
```

#### Async Request Results
```java
ChatResponse getAsyncResults(String requestId)
```
**Parameters**:
- `requestId`: ID of the async request

**Returns**: ChatResponse object

#### Follow-up Question
```java
ChatResponse processFollowUp(String requestId, String query)
```
**Parameters**:
- `requestId`: ID of the original request
- `query`: Follow-up question

**Returns**: ChatResponse object

### Execution Context API

**Create Context**:
```java
ExecutionContext ctx = new ExecutionContext();
ctx.setVariables(inputJsonNode);
ctx.setKnowledge(knowledge);
ctx.setUsersQuery(userQuery);
```

**Access Values**:
```java
JsonNode variables = ctx.getVariables();
Knowledge kb = ctx.getKnowledge();
Map<String, JsonNode> resolved = ctx.getResolvedValues();
```

---

## Best Practices

1. **Error Handling**: Always wrap external API calls with try-catch blocks
2. **Logging**: Log important state changes and errors
3. **Performance**: Use Flux/Mono for long-running operations
4. **Configuration**: Externalize configuration to properties files
5. **Documentation**: Keep JavaDoc updated with changes
6. **Testing**: Maintain high test coverage (>80%)
7. **Resource Management**: Properly close database connections and streams
8. **Security**: Validate and sanitize user inputs

---

## Troubleshooting

### Common Issues

1. **Missing Configuration**: Ensure all required properties are set in `application.properties`
2. **Authentication Errors**: Verify API keys and credentials
3. **Connection Timeouts**: Check network connectivity and endpoint availability
4. **Step Execution Failures**: Review logs for detailed error messages
5. **JSON Processing Errors**: Validate JSON structure against expected schema

### Debug Mode

Enable debug logging:
```properties
logging.level.org.symphonykernel=DEBUG
logging.level.com.microsoft.semantickernel=DEBUG
```

---

## Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Follow code style guidelines
4. Add tests for new features
5. Submit a pull request

## License

MIT License - See LICENSE file for details

## Support

For issues and questions:
- GitHub Issues: [Project Issues](https://github.com/cibinmathewjose/semanticsymphony/issues)
- Email: cibinjose@gmail.com

---

**Document Version**: 1.0  
**Last Updated**: February 2026  
**Maintained By**: Cibin Jose
