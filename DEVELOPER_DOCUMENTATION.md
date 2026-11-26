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
# symphony.document.allowed-hosts=documents.example.com
# symphony.document.follow-redirects=false
# symphony.document.vision-model=
symphony.document.chunk-size=4000
symphony.document.chunk-overlap=200
symphony.document.scanned-text-threshold=50
symphony.document.pdf-image-dpi=150

# Web Search
# symphony.websearch.foundry.endpoint=${FOUNDRY_WEBSEARCH_ENDPOINT:}
# symphony.websearch.foundry.deployment=${FOUNDRY_WEBSEARCH_DEPLOYMENT:}
# symphony.websearch.foundry.api-key=${FOUNDRY_WEBSEARCH_API_KEY:}

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

Plans and runs eligible read-only tools. If `symphony.agentic.read-only-tools` is omitted or blank, all eligible read-only tools can be considered for the planning catalog, subject to catalog limits. Set it to a comma-separated list of exact, case-sensitive names to restrict the catalog further:

```properties
# Optional restriction: only these eligible read-only tools
symphony.agentic.read-only-tools=inventory-lookup,warehouse/get-stock
# Exclude write-capable tools from agentic execution
symphony.agentic.write-tools=update-inventory,warehouse/adjust-stock
symphony.agentic.max-iterations=10
symphony.agentic.max-actions=12
symphony.agentic.timeout=PT60S
```

Optional Knowledge `data` (a JSON string):
```json
{
  "allowedTools": ["inventory-lookup"],
  "systemPrompt": "Use concise inventory terminology.",
  "maxIterations": 4
}
```

`allowedTools` can only narrow the eligible catalog and any host restriction; an empty array disables tools for that agent. `maxIterations` can only lower the host cap. The Knowledge system prompt is the fallback for `systemPrompt`; planning uses the request's model. The catalog shown to the model is enforced at dispatch, and every action in a batch must pass JSON Schema validation before any run. Schemas containing references are not supported. Local tools use their registered parameter schema; tools without one take no arguments. Earlier results can be used as evidence by later actions, but each child request gets its own validated input and payload.

Local tools are treated as read-only unless listed in `symphony.agentic.write-tools`, explicitly marked non-read-only, or of a blocked type. The write list takes precedence over both `read-only-tools` and `allowedTools`. Remove the old `verified-local-read-only-tools` property and list any write-capable tools before deploying; the planner cannot infer side effects. Use read-only credentials where possible. External MCP tools still have to pass the server's existing read-only chat eligibility checks, and the write list can exclude them by their qualified names. Nested agents, workflow delegation, authentication, email, and other blocked types remain unavailable. **Writes remain outside `AGENTIC`:** put them in an explicit Symphony workflow with a `HUMANLOOP` step and an explicit approval check before the write.

Sync and streaming save the same successful final result. Streaming emits tool start/completion/failure notices and the final answer, not model reasoning. The deadline limits how long callers wait and stops scheduling further actions; a downstream client that ignores cancellation may still finish a call already in flight. Set timeouts on downstream clients as well.

#### Default Knowledge routing

```properties
symphony.ai.autonomous=true
symphony.ai.default-knowledge=inventory-database,web-search,product-docs-search
```

Use exact names of active local Knowledge entries, not type names. Registered intent and `Key:` matches take priority regardless of the autonomy setting. On a miss, the configured default list takes priority over automatic conversation follow-ups, generated SQL, MCP discovery, and dynamic agent planning. Pending workflow input still resumes first. Without a default list, the existing fallback chain applies; disabling autonomy prevents the dynamic agent fallback but does not disable registered Knowledge matching.

In both modes, the default runner executes the eligible configured sources with the same question and generates one answer from the available results. Logs still label that runner `default-agentic`; it does not let the model select tools.

Use read-only steps that can run directly from the question without additional variables, such as `DATABASE`, `WEBSEARCH`, and `AZURE_AI_SEARCH`. The list does not bypass tool restrictions: include these names in `symphony.agentic.read-only-tools` if set, and do not list write-capable entries as defaults. Nested `AGENTIC`, `SYMPHONY`, and external MCP tools are not valid defaults. Each name is attempted once, up to four at a time, within the agentic timeout. Keep the list within `symphony.agentic.max-actions` and the hard cap of 20. Failed or timed-out sources do not prevent synthesis from the others; if none returns usable evidence, the request fails without asking the model to guess.

Only configure defaults appropriate for every caller who can reach this fallback. Each source receives the question, including public web search if enabled, and bounded excerpts of the results are sent to the configured language model.

### 9. DatabaseStep
**Class**: `org.symphonykernel.steps.DatabaseStep`

Intelligent database querying with natural language to SQL conversion.

To show generated SQL in streaming progress, set `symphony.database.stream-query=true` in the host application's Spring configuration and restart. The default is `false`. When enabled, SQL that passes the step's read-only check is sent as a status update before each execution attempt, not as final answer content. Enable only in trusted environments: every caller who can stream an applicable database step can see query literals and schema identifiers.

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

**Supported formats:** PDF (digital, scanned and mixed), Word (`.docx`, `.doc`), Excel (`.xlsx`, `.xls`), text (including CSV, JSON and XML), JPEG, PNG, BMP, GIF, and multipage TIFF. WebP requires an installed ImageIO decoder. Unsupported or encrypted files fail explicitly. Excel uses cached formula values; formulas are not recalculated. PDF vision fallback is per page and only applies to pages with insufficient extractable text; it does not perform OCR on every digital page.

**Required migration:** Set `symphony.document.allowed-hosts` to a comma-separated list of exact, trusted document hostnames. The list is empty by default. Only HTTPS on the default port or 443 is accepted, and caller request headers are not forwarded. Redirects are off by default. Set `symphony.document.follow-redirects=true` to follow up to five redirects; each destination must independently pass the same HTTPS and hostname checks. Use a short-lived signed download URL when authorization is needed. Only allow storage hosts you control, enforce outbound network policy, and avoid logging signed links in callers.

`variables.url` overrides the Knowledge URL. The system prompt comes from Knowledge JSON `SystemPrompt`, then the Knowledge system prompt, then the built-in default. The user prompt comes from `variables.userPrompt`, then Knowledge JSON `UserPrompt`, then the chat question. Set `symphony.document.vision-model` to a configured vision-capable alias if the request model cannot handle PNG inputs. Documents and rendered pages are sent to the configured AI provider; review your data-handling policy before enabling this step.

Processing is limited to 20 MiB per download, 50 PDF pages or image frames, 12 megapixels per rendered/decoded page, 8 MiB per encoded PNG, 128,000 extracted characters and 64 chunks. Page transcription and chunk extraction run sequentially per document; `parallel-threads` is no longer used. Streaming emits the final answer after preparation.

### 13. WebSearchStep
**Class**: `org.symphonykernel.steps.WebSearchStep`

`WEBSEARCH` uses the [Foundry Models Responses API](https://learn.microsoft.com/azure/foundry/openai/how-to/web-search) with hosted `web_search` (Web IQ). No persistent agent, Bing key, or second LLM call is required.

1. Use an Azure OpenAI deployment that supports the tool and verify that Web Search isn't blocked in your subscription. Set `symphony.websearch.foundry.endpoint` to the resource origin (for example, `https://your-resource.openai.azure.com`) and `symphony.websearch.foundry.deployment` to the deployment name.
2. For Entra authentication, expose a cached Azure SDK `TokenCredential` (such as managed identity) as a Spring bean named `foundryWebSearchCredential`, and grant it inference permissions on the Azure OpenAI resource. The step requests `https://ai.azure.com/.default`. Without this bean, configure `symphony.websearch.foundry.api-key` from a secret store. Credentials and endpoint are never read from workflow data.
3. Keep existing knowledge entries of type `WEBSEARCH`. Remove legacy `provider`, `count`, and Google/Bing/SerpApi key settings; they are ignored. Optional `SystemPrompt` in the Knowledge data JSON is passed as supplemental instructions. `variables.query` takes precedence over the user's query.

The step always returns one item with `query`, `summary`, and `sources` (deduplicated `title`/`url` citations), or an `errors` item. Streaming emits the same JSON array in one chunk after completion. Update consumers that expect `results`, snippets, or token deltas, and display citation links with the answer. Review [Web IQ terms](https://www.microsoft.com/en-us/webiq/webiq-legal), billing, and the data boundary before enabling this step for user queries or sending internal data to it.

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

Streamable HTTP is the default. Set `symphony.mcp.client.transport=sse` for a legacy default, or
`auto` to try legacy SSE before Streamable HTTP. A per-server `transport` overrides the global choice
in Spring configuration or in flat and nested MCP knowledge registrations.

Register multiple Spring servers by using successive indexes, or list multiple entries under `mcpServers`
in one MCP knowledge document. Chat tries configured MCP servers when normal knowledge and SQL routing
do not match. Set `symphony.mcp.client.chat-fallback=false` to disable this fallback. A dedicated `MCP`
knowledge entry can scope discovery to a named server, to its nested `mcpServers` entries, or to all
registered servers with `{"discover":true}`. The model uses qualified tool IDs across multiple servers,
so two servers can expose the same tool name. An unreachable server does not block the remaining catalog.

The model answers capability questions from discovery without executing tools. For a live lookup it
can call one eligible tool per turn. Related chat follow-ups retain the MCP scope and recent context.
Tools marked read-only
by a trusted server are eligible. If the server omits annotations, operators can list verified read-only
tools using `symphony.mcp.client.servers[0].chat-read-only-tools[0]=lookup_example`, or
`"chatReadOnlyTools": ["lookup_example"]` in the server's knowledge configuration. Never list tools
that change state. Explicit `tool` and `arguments` payloads continue to use the direct dispatch path.

**Workflow steps:** Create an `MCP` knowledge entry with `{"name":"warehouse","tool":"get_stock"}`
where `warehouse` is a registered server. In the Symphony `Flow`, reference that knowledge entry with
`Name` and choose a `Key` such as `stock`. Its `Payload` can be `input` or a prior result key; use a
JSON object of tool arguments, or an object containing `arguments`. To let a workflow step choose an
eligible read-only tool from its catalog, omit `tool` from its knowledge configuration. Payload data
does not override the workflow's tool selection. Later stages can reuse the native result with `"Payload":"stock"` or
reference it in prompts as `{{$stock}}`. A `HUMANLOOP` stage can use the same data in its question;
after the reply, execution resumes with the earlier values and the reply under the human stage's key,
without repeating completed MCP calls. Workflow follow-ups can use persisted stage results.

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
