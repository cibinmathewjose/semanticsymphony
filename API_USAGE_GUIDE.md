# API Usage Guide and Examples

## Table of Contents
1. [Basic Usage](#basic-usage)
2. [Request and Response Examples](#request-and-response-examples)
3. [Advanced Workflows](#advanced-workflows)
4. [Agentic Workflows](#agentic-workflows)
5. [MCP Integration](#mcp-integration)
6. [Email Step](#email-step)
7. [Database Intelligence](#database-intelligence)
8. [Document Processing](#document-processing)
9. [Web Search](#web-search)
10. [Human-in-the-Loop](#human-in-the-loop)
11. [Authentication Step](#authentication-step)
12. [Error Handling](#error-handling)
13. [Configuration Examples](#configuration-examples)
14. [Integration Examples](#integration-examples)

---

## Basic Usage

### Setting Up the Framework

#### 1. Spring Boot Application Setup

```java
@SpringBootApplication
@EnableAutoConfiguration
public class SymphonyApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(SymphonyApplication.class, args);
    }
}
```

#### 2. Dependency Injection

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    @Autowired
    private Agent agent;
    
    @PostMapping("/message")
    public ChatResponse sendMessage(@RequestBody ChatRequest request) {
        return agent.process(request);
    }
}
```

### Basic Chat Request

```java
// Create a simple chat request
ChatRequest request = new ChatRequest();
request.setQuery("What is the weather today?");
request.setUser("user_123");
request.setSession("session_abc");

// Process the request
Agent agent = applicationContext.getBean(Agent.class);
ChatResponse response = agent.process(request);

// Display the response
System.out.println("Response: " + response.getMessage());
System.out.println("Status: " + response.getStatusCode());
```

---

## Request and Response Examples

### Example 1: Simple Query Processing

**Request**:
```json
{
  "query": "Get all customers",
  "user": "john.doe",
  "session": "sess_12345",
  "conversationId": "conv_001"
}
```

**Code**:
```java
ChatRequest request = new ChatRequest();
request.setQuery("Get all customers");
request.setUser("john.doe");
request.setSession("sess_12345");
request.setConversationId("conv_001");

ChatResponse response = agent.process(request);
```

**Response**:
```json
{
  "requestId": "req_789",
  "message": "Successfully retrieved customers",
  "statusCode": "200",
  "data": [
    {
      "id": 1,
      "name": "Acme Corp",
      "email": "contact@acme.com"
    },
    {
      "id": 2,
      "name": "TechStart Inc",
      "email": "hello@techstart.com"
    }
  ]
}
```

### Example 2: Query with Parameters

**Request with Context**:
```java
ChatRequest request = new ChatRequest();
request.setQuery("Find sales for product {productId}");
request.setUser("sales_rep_01");

// Add context information
Map<String, String> context = new HashMap<>();
context.put("productId", "PROD-001");
context.put("department", "Sales");
request.setContextInfo(context);

ChatResponse response = agent.process(request);
```

### Example 3: Multi-Turn Conversation

**First Turn**:
```java
ChatRequest firstRequest = new ChatRequest();
firstRequest.setQuery("What is our top product?");
firstRequest.setUser("analyst_001");
firstRequest.setSession("session_xyz");

ChatResponse firstResponse = agent.process(firstRequest);
String requestId = firstResponse.getRequestId();
```

**Follow-up Turn**:
```java
// Process follow-up question
ChatResponse followUpResponse = agent.processFollowUp(
    requestId, 
    "How much revenue did it generate last quarter?"
);

System.out.println(followUpResponse.getMessage());
```

### Example 4: Streaming Response

```java
ChatRequest request = new ChatRequest();
request.setQuery("Generate a detailed report");
request.setUser("manager_001");

// Get streaming response
Flux<String> responseStream = agent.streamProcess(request);

// Subscribe to stream
responseStream
    .doOnNext(chunk -> System.out.print(chunk))
    .doOnError(error -> System.err.println("Error: " + error.getMessage()))
    .doOnComplete(() -> System.out.println("\nCompleted"))
    .subscribe();
```

---

## Advanced Workflows

### Example 1: Multi-Step Workflow

Define a workflow that executes multiple steps:

**Knowledge Base Entry** (`knowledge.data`):
```json
{
  "id": "customer_analysis",
  "items": [
    {
      "id": "fetch_customers",
      "type": "SQL",
      "query": "SELECT * FROM customers WHERE active = true",
      "dataSource": "primary"
    },
    {
      "id": "fetch_orders",
      "type": "REST",
      "method": "GET",
      "url": "https://api.example.com/orders",
      "headers": { "Authorization": "Bearer ${headers.authorization}" }
    },
    {
      "id": "correlate_data",
      "type": "Velocity",
      "template": "#set($result = {})#foreach($customer in $fetch_customers)$result.put($customer.id, null)#end$result"
    },
    {
      "id": "generate_report",
      "type": "Velocity",
      "template": "Customer Report Generated:\n#foreach($customer in $fetch_customers)- ${customer.name}\n#end"
    }
  ],
  "output": "${generate_report.result}"
}
```

**Processing**:
```java
ChatRequest request = new ChatRequest();
request.setQuery("Generate customer analysis report");
request.setUser("analyst");

ChatResponse response = agent.process(request);
System.out.println(response.getMessage());
```

### Example 2: REST API Integration

**REST Step Configuration**:
```json
{
  "id": "external_api_call",
  "type": "REST",
  "method": "POST",
  "url": "https://api.example.com/data/query",
  "headers": {
    "Content-Type": "application/json",
    "Authorization": "Bearer ${contextInfo.apiToken}"
  },
  "body": {
    "filters": {
      "department": "${contextInfo.department}",
      "date_from": "${variables.startDate}",
      "date_to": "${variables.endDate}"
    }
  },
  "timeout": 30000
}
```

**Java Implementation**:
```java
ChatRequest request = new ChatRequest();
request.setQuery("Fetch quarterly revenue");

Map<String, String> context = new HashMap<>();
context.put("apiToken", "your_api_token");
context.put("department", "Finance");
request.setContextInfo(context);

ChatResponse response = agent.process(request);
```

### Example 3: GraphQL Query

**GraphQL Step Configuration**:
```json
{
  "id": "graphql_query",
  "type": "GraphQL",
  "endpoint": "https://api.example.com/graphql",
  "query": "query GetProducts($limit: Int!) { products(limit: $limit) { id name price rating } }",
  "variables": {
    "limit": 10
  }
}
```

**Processing**:
```java
ChatRequest request = new ChatRequest();
request.setQuery("Get top 10 products");
request.setUser("customer");

ChatResponse response = agent.process(request);
ArrayNode products = response.getData();
```

### Example 4: Database Operations

**SQL Step Configuration**:
```json
{
  "id": "customer_query",
  "type": "SQL",
  "dataSource": "primary",
  "query": "SELECT id, name, email, status FROM customers WHERE created_date > ? AND status = ? ORDER BY created_date DESC LIMIT ?",
  "params": ["${variables.fromDate}", "active", "${variables.limit}"]
}
```

**Processing**:
```java
ChatRequest request = new ChatRequest();
request.setQuery("Get recently registered customers");
request.setUser("admin");

// Add variables
ExecutionContext ctx = new ExecutionContext();
ctx.setVariables(objectMapper.createObjectNode()
    .put("fromDate", "2024-01-01")
    .put("limit", 50));

ChatResponse response = agent.process(request);
```

### Example 5: Plugin Integration

**Custom Plugin**:
```java
public class AnalyticsPlugin {
    
    public JsonNode calculateMetrics(JsonNode data) {
        // Process data
        ObjectNode result = new ObjectMapper().createObjectNode();
        result.put("totalRecords", data.size());
        result.put("averageValue", calculateAverage(data));
        return result;
    }
    
    public JsonNode generateChart(JsonNode data) {
        // Generate chart data
        return data;
    }
}
```

**Workflow Using Plugin**:
```json
{
  "id": "analytics_workflow",
  "items": [
    {
      "id": "fetch_data",
      "type": "SQL",
      "query": "SELECT * FROM metrics WHERE month = ?"
    },
    {
      "id": "calculate",
      "type": "Plugin",
      "pluginName": "AnalyticsPlugin",
      "function": "calculateMetrics",
      "parameters": {
        "data": "${fetch_data.result}"
      }
    },
    {
      "id": "visualize",
      "type": "Plugin",
      "pluginName": "AnalyticsPlugin",
      "function": "generateChart",
      "parameters": {
        "data": "${calculate.result}"
      }
    }
  ],
  "output": "${visualize.result}"
}
```

**Processing**:
```java
ChatRequest request = new ChatRequest();
request.setQuery("Analyze monthly metrics");
request.setUser("analyst");

ChatResponse response = agent.process(request);
JsonNode chartData = response.getData();
```

---

## Agentic Workflows

The Agentic step uses the ReAct (Reason + Act) pattern where the LLM dynamically plans which tools to use.

### Example 1: Basic Agentic Request

```java
// The Agentic step doesn't require predefined flows — the LLM decides which tools to call
ChatRequest request = new ChatRequest();
request.setQuery("Find recent orders for customer ACME Corp and send a summary email to the sales team");
request.setUser("analyst_001");

ChatResponse response = agent.process(request);
```

### Example 2: Agentic Knowledge Configuration

```json
{
  "name": "smart-assistant",
  "type": "Agentic",
  "description": "An intelligent assistant that can access databases, APIs, and send emails",
  "systemPrompt": "You are a helpful business assistant. Use the available tools to answer questions about customers, orders, and products. Always verify data before sending emails.",
  "data": {
    "maxIterations": 10
  }
}
```

### Example 3: Agentic with MCP Tools

When MCP client is enabled, the agentic planner can use both Symphony steps and external MCP tools:

```properties
# Enable MCP client to augment agentic planning with external tools
symphony.mcp.client.enabled=true
symphony.mcp.client.servers[0].name=filesystem-server
symphony.mcp.client.servers[0].url=http://localhost:3001
symphony.mcp.client.servers[1].name=database-server
symphony.mcp.client.servers[1].url=http://localhost:3002
```

---

## MCP Integration

### MCP Server: Expose Symphony Tools

External agents can discover and call Symphony steps as MCP tools.

**Configuration**:
```properties
symphony.mcp.server.enabled=true
spring.ai.mcp.server.name=symphony-kernel
spring.ai.mcp.server.version=1.0.0
```

Once enabled, all knowledge base entries are automatically registered as MCP tools via SSE transport. External tools like Claude Desktop or Cursor can connect and use them.

### MCP Client: Consume External Tools

```properties
symphony.mcp.client.enabled=true
symphony.mcp.client.servers[0].name=weather-server
symphony.mcp.client.servers[0].url=http://localhost:3001
symphony.mcp.client.servers[1].name=file-server
symphony.mcp.client.servers[1].url=http://localhost:3002
```

**Programmatic Usage**:
```java
@Autowired
private MCPToolRegistry toolRegistry;

// List all available tools (Symphony + external MCP)
List<MCPToolDescriptor> tools = toolRegistry.getAllTools();

// Tools are automatically available in Agentic planning
```

---

## Email Step

### Example 1: Simple Email Notification

**Knowledge Configuration**:
```json
{
  "name": "send-report-email",
  "type": "Email",
  "description": "Send a report email to the team",
  "data": {
    "to": ["team@example.com"],
    "subject": "Daily Report - {{reportDate}}",
    "body": "<h1>Daily Report</h1><p>{{reportContent}}</p>",
    "html": true
  }
}
```

### Example 2: Email in a Multi-Step Workflow

```json
{
  "id": "report-and-email",
  "items": [
    {
      "id": "fetch_data",
      "type": "SQL",
      "query": "SELECT * FROM daily_metrics WHERE date = CURRENT_DATE"
    },
    {
      "id": "format_report",
      "type": "Velocity",
      "template": "#foreach($row in $fetch_data)- ${row.metric}: ${row.value}\n#end"
    },
    {
      "id": "send_email",
      "type": "Email",
      "to": ["manager@example.com"],
      "cc": ["team@example.com"],
      "subject": "Daily Metrics Report",
      "body": "<pre>{{format_report}}</pre>",
      "html": true
    }
  ]
}
```

---

## Database Intelligence

The DatabaseStep provides natural language to SQL conversion with schema introspection.

### Example 1: Natural Language SQL Query

**Knowledge Configuration**:
```json
{
  "name": "query-sales-db",
  "type": "Database",
  "description": "Query the sales database using natural language",
  "data": {
    "dbName": "salesdb",
    "maxRows": 100
  }
}
```

**Database Connection** (`application.properties`):
```properties
symphony.db.salesdb.url=jdbc:postgresql://localhost:5432/sales
symphony.db.salesdb.username=${DB_USER}
symphony.db.salesdb.password=${DB_PASS}
symphony.db.salesdb.driver-class-name=org.postgresql.Driver
```

**Usage**:
```java
ChatRequest request = new ChatRequest();
request.setQuery("Show me the top 10 customers by revenue last quarter");
// The LLM will introspect the schema and generate appropriate SQL
ChatResponse response = agent.process(request);
```

**Safety**: The DatabaseStep enforces read-only queries — INSERT, UPDATE, DELETE, and DROP statements are blocked.

---

## Document Processing

The DocumentStep processes multiple document formats including PDF, DOCX, Excel, and images.

### Example 1: Analyze a PDF Document

**Knowledge Configuration**:
```json
{
  "name": "analyze-document",
  "type": "Document",
  "description": "Extract and analyze document content",
  "data": {
    "chunkSize": 4000,
    "chunkOverlap": 200,
    "parallelThreads": 4
  }
}
```

**Configuration** (`application.properties`):
```properties
symphony.document.chunk-size=4000
symphony.document.chunk-overlap=200
symphony.document.scanned-text-threshold=50
symphony.document.pdf-image-dpi=150
symphony.document.parallel-threads=4
```

**Supported Formats**: PDF (text + scanned), DOCX, Excel (.xlsx, .xls), plain text, images (JPEG, PNG, TIFF, BMP, GIF, WebP)

---

## Web Search

The WebSearchStep integrates internet search with optional LLM summarization.

### Example 1: Search the Web

**Knowledge Configuration**:
```json
{
  "name": "web-search",
  "type": "WebSearch",
  "description": "Search the internet for information",
  "data": {
    "provider": "bing",
    "resultCount": 5,
    "summarize": true
  }
}
```

**Configuration** (`application.properties`):
```properties
symphony.websearch.bing.api-key=${BING_API_KEY}
# Or for Google:
# symphony.websearch.google.api-key=${GOOGLE_API_KEY}
# symphony.websearch.google.cx=${GOOGLE_CX}
```

**Providers**: `bing`, `google`, `serpapi`

---

## Human-in-the-Loop

The HumanInLoopStep pauses workflow execution to collect user input.

### Example 1: Approval Workflow

```json
{
  "id": "approval-workflow",
  "items": [
    {
      "id": "fetch_request",
      "type": "SQL",
      "query": "SELECT * FROM purchase_requests WHERE id = ?"
    },
    {
      "id": "approval",
      "type": "HumanInLoop",
      "question": "Approve purchase request for {{fetch_request.item_name}} (${{fetch_request.amount}})?",
      "options": ["Approve", "Reject", "Request More Info"],
      "timeout": 300000,
      "defaultOption": "Reject"
    },
    {
      "id": "update_status",
      "type": "SQL",
      "query": "UPDATE purchase_requests SET status = ? WHERE id = ?",
      "params": ["${approval.result}", "${fetch_request.id}"]
    }
  ]
}
```

---

## Authentication Step

The AuthenticationStep acquires OAuth2 tokens for downstream API calls.

### Example 1: OAuth2 Token Acquisition

```json
{
  "id": "authenticated-api-call",
  "items": [
    {
      "id": "get_token",
      "type": "Authentication",
      "tokenEndpoint": "https://auth.example.com/oauth2/token",
      "clientId": "${contextInfo.clientId}",
      "clientSecret": "${contextInfo.clientSecret}",
      "headerName": "Authorization"
    },
    {
      "id": "call_api",
      "type": "REST",
      "method": "GET",
      "url": "https://api.example.com/protected/data",
      "headers": {
        "Authorization": "${get_token.token}"
      }
    }
  ]
}
```

---

## Error Handling

### Example 1: Basic Error Handling

```java
try {
    ChatRequest request = new ChatRequest();
    request.setQuery("Process data");
    
    ChatResponse response = agent.process(request);
    
    if ("200".equals(response.getStatusCode())) {
        System.out.println("Success: " + response.getMessage());
    } else {
        System.err.println("Error: " + response.getMessage());
        System.err.println("Status: " + response.getStatusCode());
    }
} catch (IllegalArgumentException e) {
    System.err.println("Invalid request: " + e.getMessage());
} catch (Exception e) {
    System.err.println("Unexpected error: " + e.getMessage());
    e.printStackTrace();
}
```

### Example 2: Stream Error Handling

```java
ChatRequest request = new ChatRequest();
request.setQuery("Stream data");

agent.streamProcess(request)
    .doOnNext(data -> {
        System.out.println("Received: " + data);
    })
    .doOnError(error -> {
        if (error instanceof TimeoutException) {
            System.err.println("Request timeout");
        } else if (error instanceof AuthenticationException) {
            System.err.println("Authentication failed");
        } else {
            System.err.println("Error: " + error.getMessage());
        }
    })
    .doOnComplete(() -> {
        System.out.println("Stream completed successfully");
    })
    .subscribe();
```

### Example 3: Validation and Fallback

```java
ChatResponse processWithFallback(ChatRequest request) {
    try {
        // Validate request
        if (request == null || request.getQuery() == null || request.getQuery().isEmpty()) {
            ChatResponse fallback = new ChatResponse("Invalid request");
            fallback.setStatusCode("400");
            return fallback;
        }
        
        // Process request
        return agent.process(request);
        
    } catch (Exception e) {
        // Fallback response
        ChatResponse error = new ChatResponse("Error processing request: " + e.getMessage());
        error.setStatusCode("500");
        return error;
    }
}
```

---

## Configuration Examples

### Example 1: Azure OpenAI Configuration

```properties
# Azure OpenAI
client.symphonykernel.azureopenaikey=${AZURE_OPENAI_API_KEY}
client.symphonykernel.azureopenaiendpoint=${AZURE_OPENAI_ENDPOINT}
client.symphonykernel.azureopenaideploymentname=gpt-4o

# Vector Intent Matching
symphony.intent.vector.enabled=true
symphony.intent.vector.similarity-threshold=0.78
symphony.intent.vector.top-k=3
spring.ai.azure.openai.embedding.options.deployment-name=text-embedding-ada-002

# Logging
logging.level.org.symphonykernel=INFO
logging.level.org.springframework.ai=DEBUG
```

### Example 2: Database Configuration

```properties
# Primary Database
spring.datasource.url=jdbc:mysql://localhost:3306/symphony_db
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# Connection Pool
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect
```

### Example 3: Redis Configuration

```properties
# Redis Cache
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.password=${REDIS_PASSWORD}
spring.redis.timeout=2000
spring.redis.database=0

# Cache Configuration
spring.cache.type=redis
spring.cache.redis.time-to-live=3600000
```

### Example 4: Azure Search Configuration

```properties
# Azure AI Search
spring.ai.azure.search.uri=${AZURE_SEARCH_URI}
spring.ai.azure.search.key=${AZURE_SEARCH_KEY}
spring.ai.azure.search.index-name=documents
spring.ai.azure.search.similarity-k=10
```

---

## Integration Examples

### Example 1: REST Controller Integration

```java
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {
    
    @Autowired
    private Agent agent;
    
    @PostMapping("/message")
    public ResponseEntity<ChatResponse> sendMessage(
            @RequestBody ChatRequest request,
            HttpServletRequest httpRequest) {
        try {
            // Set HTTP headers
            request.setHttpHeaderProvider(() -> {
                // Extract headers from HttpServletRequest
                return new HashMap<>();
            });
            
            ChatResponse response = agent.process(request);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            ChatResponse errorResponse = new ChatResponse("Error: " + e.getMessage());
            errorResponse.setStatusCode("500");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    @GetMapping("/stream/{requestId}")
    public Flux<String> streamMessage(@PathVariable String requestId) {
        ChatRequest request = new ChatRequest();
        request.setKey(requestId);
        return agent.streamProcess(request);
    }
    
    @PostMapping("/followup/{requestId}")
    public ResponseEntity<ChatResponse> followUpQuestion(
            @PathVariable String requestId,
            @RequestBody ChatRequest followUpRequest) {
        ChatResponse response = agent.processFollowUp(requestId, followUpRequest.getQuery());
        return ResponseEntity.ok(response);
    }
}
```

### Example 2: Service Layer Integration

```java
@Service
public class CustomerService {
    
    @Autowired
    private Agent agent;
    
    public List<Customer> getCustomersByQuery(String query, String user) {
        ChatRequest request = new ChatRequest();
        request.setQuery(query);
        request.setUser(user);
        
        ChatResponse response = agent.process(request);
        
        // Convert response to Customer list
        return response.getData()
            .map(customer -> {
                Customer c = new Customer();
                c.setId(customer.get("id").asText());
                c.setName(customer.get("name").asText());
                c.setEmail(customer.get("email").asText());
                return c;
            })
            .collect(Collectors.toList());
    }
    
    public Flux<Customer> streamCustomers(String query, String user) {
        ChatRequest request = new ChatRequest();
        request.setQuery(query);
        request.setUser(user);
        
        return agent.streamProcess(request)
            .map(json -> parseCustomer(json))
            .onErrorResume(error -> {
                logger.error("Error streaming customers", error);
                return Flux.empty();
            });
    }
}
```

### Example 3: Batch Processing

```java
@Service
public class BatchProcessingService {
    
    @Autowired
    private Agent agent;
    
    public void processBatch(List<String> queries, String user) {
        queries.parallelStream()
            .map(query -> {
                ChatRequest request = new ChatRequest();
                request.setQuery(query);
                request.setUser(user);
                return agent.process(request);
            })
            .forEach(response -> {
                if ("200".equals(response.getStatusCode())) {
                    // Process successful response
                    storeResults(response);
                } else {
                    // Log error
                    logError(response);
                }
            });
    }
    
    public Flux<ChatResponse> processAsyncBatch(List<String> queries, String user) {
        return Flux.fromIterable(queries)
            .flatMap(query -> {
                ChatRequest request = new ChatRequest();
                request.setQuery(query);
                request.setUser(user);
                
                return Mono.fromCallable(() -> agent.process(request))
                    .subscribeOn(Schedulers.boundedElastic());
            });
    }
}
```

### Example 4: Custom Knowledge Base Implementation

```java
@Service
public class CustomKnowledgeBaseService implements IknowledgeBase {
    
    @Autowired
    private KnowledgeRepository repository;
    
    @Autowired
    @Qualifier("redisCacheManager")
    private CacheManager cacheManager;
    
    @Override
    public Knowledge GetByName(String name) {
        // Try cache first
        Cache cache = cacheManager.getCache("knowledge");
        if (cache != null) {
            Knowledge cached = cache.get(name, Knowledge.class);
            if (cached != null) {
                return cached;
            }
        }
        
        // Load from database
        Knowledge kb = repository.findByName(name);
        
        // Cache it
        if (cache != null && kb != null) {
            cache.put(name, kb);
        }
        
        return kb;
    }
    
    // Implement other methods
}
```

---

## Best Practices for API Usage

1. **Always Validate Input**
   ```java
   if (request == null || request.getQuery() == null || request.getQuery().isEmpty()) {
       throw new IllegalArgumentException("Invalid request");
   }
   ```

2. **Use Try-Catch for External Calls**
   ```java
   try {
       ChatResponse response = agent.process(request);
   } catch (Exception e) {
       logger.error("Error processing request", e);
       return fallbackResponse();
   }
   ```

3. **Implement Proper Logging**
   ```java
   logger.info("Processing request: {} for user: {}", requestId, userId);
   logger.debug("Variables: {}", variables);
   logger.error("Error in step: {}", stepName, exception);
   ```

4. **Use Streaming for Large Results**
   ```java
   Flux<String> stream = agent.streamProcess(request);
   stream.buffer(100).subscribe(batch -> processBatch(batch));
   ```

5. **Implement Proper Timeout Handling**
   ```java
   agent.streamProcess(request)
       .timeout(Duration.ofSeconds(30))
       .subscribe();
   ```

---

**Document Version**: 1.0  
**Last Updated**: March 2026
