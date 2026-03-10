# Symphony AI - Developer Documentation

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

**Symphony AI** is an intelligent chat agentic framework built on Spring AI and Spring Boot. It provides a comprehensive solution for building sophisticated AI-driven applications with support for multiple data sources, knowledge bases, agentic workflows, and the Model Context Protocol (MCP).

### Key Features
- **Agentic AI Framework**: ReAct-style autonomous planning and execution via Spring AI
- **Multi-Step Workflows**: Execute complex workflows with 12+ step types (SQL, REST, GraphQL, Agentic, Database, Email, Document, WebSearch, HumanInLoop, and more)
- **MCP Protocol Support**: Expose Symphony steps as MCP tools and consume external MCP servers
- **Knowledge Graph Integration**: Build and query knowledge graphs
- **Vector Search Support**: Azure AI Search integration for semantic intent matching
- **Plugin System**: Extensible plugin architecture
- **Session Management**: Track and manage user sessions
- **Stream Processing**: Reactive streams for real-time response handling
- **Multiple AI Providers**: Azure OpenAI and Anthropic via Spring AI
- **Document Processing**: PDF, DOCX, Excel, images (including scanned/OCR)
- **Email Notifications**: SMTP-based email with template resolution
- **Web Search**: Bing, Google, and SerpAPI integration
- **Human-in-the-Loop**: Pause workflows for user input
- **Database Intelligence**: Natural language to SQL with schema introspection

### Technology Stack
- **Java**: JDK 17+
- **Framework**: Spring Boot 3.4.2
- **Core Dependencies**:
  - Spring AI 1.1.2 (Azure OpenAI, Anthropic, MCP)
  - Azure AI Search 11.8.0
  - Jackson for JSON processing
  - Reactor for reactive programming
  - Redis for caching
  - Apache POI 5.4.1 for document processing
  - Apache PDFBox 3.0.4 for PDF handling
  - Apache Velocity 2.4.1 for template engine

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
│  ┌────────┬────────┬────────┬────────┬──────────┬──────────┐ │
│  │ REST   │ SQL    │GraphQL │ Plugin │ Agentic  │ Database │ │
│  │ Step   │ Step   │ Step   │ Step   │ Step     │ Step     │ │
│  ├────────┼────────┼────────┼────────┼──────────┼──────────┤ │
│  │ Email  │Document│WebSearch│Velocity│HumanIn  │  Auth    │ │
│  │ Step   │ Step   │ Step   │ Step   │LoopStep │  Step    │ │
│  └────────┴────────┴────────┴────────┴──────────┴──────────┘ │
├──────────────────────────────────────────────────────────────┤
│            MCP Server / MCP Client Integration               │
├──────────────────────────────────────────────────────────────┤
│          AI Client (Azure OpenAI / Anthropic via Spring AI)  │
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

Symphony AI is published on **Maven Central**. Add to your `pom.xml`:

```xml
<dependency>
    <groupId>org.symphonykernel</groupId>
    <artifactId>symphony-ai</artifactId>
    <version>0.1.0</version>
</dependency>
```

No additional repository configuration is needed — Maven Central is used by default.

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
client.symphonykernel.azureopenaikey=${AZURE_OPENAI_API_KEY}
client.symphonykernel.azureopenaiendpoint=${AZURE_OPENAI_ENDPOINT}
client.symphonykernel.azureopenaideploymentname=gpt-4o

# Vector Intent Matching
symphony.intent.vector.enabled=true
symphony.intent.vector.similarity-threshold=0.78
symphony.intent.vector.top-k=3
spring.ai.azure.openai.embedding.options.deployment-name=text-embedding-ada-002

# MCP Server (Expose Symphony steps as tools)
symphony.mcp.server.enabled=false
# spring.ai.mcp.server.name=symphony-kernel
# spring.ai.mcp.server.version=1.0.0

# MCP Client (Connect to external MCP servers)
symphony.mcp.client.enabled=false
# symphony.mcp.client.servers[0].name=example-server
# symphony.mcp.client.servers[0].url=http://localhost:3001

# Agentic Configuration
symphony.agentic.max-iterations=10

# Database Configuration (per-database)
# symphony.db.<dbname>.url=
# symphony.db.<dbname>.username=
# symphony.db.<dbname>.password=
# symphony.db.<dbname>.driver-class-name=

# Document Processing
symphony.document.chunk-size=4000
symphony.document.chunk-overlap=200
symphony.document.scanned-text-threshold=50
symphony.document.pdf-image-dpi=150
symphony.document.parallel-threads=4

# Web Search
# symphony.websearch.bing.api-key=
# symphony.websearch.google.api-key=
# symphony.websearch.google.cx=

# Email (Spring Mail)
spring.mail.host=smtp.example.com
spring.mail.port=587
spring.mail.username=your-email@example.com
spring.mail.password=your-password
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

# Redis Configuration
spring.redis.host=${REDIS_HOST}
spring.redis.port=${REDIS_PORT}
```

### Spring Boot Auto-Configuration

The framework provides auto-configuration through `SymphonyKernelAutoConfiguration`:

**Key Configuration Classes**:
1. `AzureOpenAiConfig`: Azure OpenAI service setup
2. `VelocityEngineConfig`: Velocity template engine
3. `AzureAISearchConnectionProperties`: Vector search configuration
4. `DBConnectionProperties`: Multi-database connections
5. `RedisConnectionProperties`: Cache configuration
6. `SymphonyConfig`: Agent mode configuration (autonomous, thread pool, cache TTL)
7. `MCPServerConfig`: MCP server setup
8. `MCPClientProperties`: MCP client configuration
9. `SharePointConfig`: SharePoint integration setup

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

### 8. AgenticStep
**Class**: `org.symphonykernel.steps.AgenticStep`

Executes agentic workflows using the ReAct (Reason + Act) pattern. The LLM dynamically plans which tools to use without predefined flows.

**Configuration**:
```json
{
  "type": "Agentic",
  "systemPrompt": "You are a helpful assistant with access to tools.",
  "maxIterations": 10
}
```

**Features**:
- LLM-driven dynamic planning
- Supports both Symphony steps and external MCP tools
- Configurable max iterations (default: 10)
- Thread pool semaphore for concurrent LLM calls

### 9. DatabaseStep
**Class**: `org.symphonykernel.steps.DatabaseStep`

Intelligent database querying with natural language to SQL conversion.

**Configuration**:
```json
{
  "type": "Database",
  "dbName": "mydb",
  "maxRows": 100
}
```

**Features**:
- Schema introspection (tables, views, columns)
- LLM-generated SQL from natural language
- Multi-database support (Oracle, SQL Server, MySQL, PostgreSQL)
- Read-only query enforcement (prevents INSERT/UPDATE/DELETE/DROP)
- Configurable per-database JDBC connections

### 10. AuthenticationStep
**Class**: `org.symphonykernel.steps.AuthenticationStep`

OAuth2/token acquisition for downstream API calls.

**Configuration**:
```json
{
  "type": "Authentication",
  "tokenEndpoint": "https://auth.example.com/token",
  "clientId": "your-client-id",
  "clientSecret": "${contextInfo.clientSecret}",
  "headerName": "Authorization"
}
```

### 11. EmailStep
**Class**: `org.symphonykernel.steps.EmailStep`

Sends emails with template resolution via Spring Mail.

**Configuration**:
```json
{
  "type": "Email",
  "to": ["user@example.com"],
  "cc": [],
  "bcc": [],
  "subject": "Report for {{reportName}}",
  "body": "<h1>Hello {{userName}}</h1><p>Your report is ready.</p>",
  "html": true
}
```

**Features**:
- Per-step SMTP configuration
- Template placeholder resolution (`{{key}}` syntax)
- TO, CC, BCC support
- HTML and plain text modes

### 12. DocumentStep
**Class**: `org.symphonykernel.steps.DocumentStep`

Multi-format document analysis and extraction.

**Supported Formats**: PDF (text + scanned), DOCX, Excel, plain text, images (JPEG, PNG, TIFF, BMP, GIF, WebP)

**Configuration**:
```json
{
  "type": "Document",
  "chunkSize": 4000,
  "chunkOverlap": 200,
  "parallelThreads": 4
}
```

**Features**:
- Chunk-based processing with overlap
- Parallel processing (configurable threads)
- Vision model support for scanned pages and images

### 13. WebSearchStep
**Class**: `org.symphonykernel.steps.WebSearchStep`

Internet search with optional LLM summarization.

**Configuration**:
```json
{
  "type": "WebSearch",
  "provider": "bing",
  "resultCount": 5,
  "summarize": true
}
```

**Providers**: Bing, Google, SerpAPI

### 14. HumanInLoopStep
**Class**: `org.symphonykernel.steps.HumanInLoopStep`

Pauses workflow execution for user input.

**Configuration**:
```json
{
  "type": "HumanInLoop",
  "question": "Please confirm the action for {{itemName}}:",
  "options": ["Approve", "Reject", "Modify"],
  "timeout": 300000,
  "defaultOption": "Reject"
}
```

**Features**:
- Question with `{{placeholder}}` resolution
- Multiple choice options
- Timeout handling with default values
- Blocks workflow until user submits

---

## MCP (Model Context Protocol) Integration

Symphony AI supports the [Model Context Protocol](https://modelcontextprotocol.io/) for tool interoperability.

### MCP Server (Expose Symphony as tools)

External agents (e.g., Claude Desktop, Cursor, other MCP clients) can discover and call Symphony knowledge steps as MCP tools.

**Enable**:
```properties
symphony.mcp.server.enabled=true
spring.ai.mcp.server.name=symphony-kernel
spring.ai.mcp.server.version=1.0.0
```

**How it works**: `MCPServerConfig` registers all knowledge base entries as MCP tools via SSE transport. External agents can then discover and invoke them.

### MCP Client (Consume external MCP servers)

Symphony can connect to external MCP servers and use their tools within workflows and agentic planning.

**Enable**:
```properties
symphony.mcp.client.enabled=true
symphony.mcp.client.servers[0].name=example-server
symphony.mcp.client.servers[0].url=http://localhost:3001
```

**Key Classes**:
- `MCPClientService`: Connects to external MCP servers, discovers tools
- `MCPToolRegistry`: Central registry of all available tools (Symphony + external)
- `MCPToolDescriptor`: Tool metadata for registration

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
logging.level.org.springframework.ai=DEBUG
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
**Last Updated**: March 2026  
**Maintained By**: Cibin Jose
