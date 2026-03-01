# Troubleshooting Guide and Common Issues

## Table of Contents
1. [Installation and Setup Issues](#installation-and-setup-issues)
2. [Configuration Issues](#configuration-issues)
3. [Runtime Errors](#runtime-errors)
4. [Performance Issues](#performance-issues)
5. [Integration Issues](#integration-issues)
6. [Debugging Techniques](#debugging-techniques)
7. [FAQ](#faq)

---

## Installation and Setup Issues

### Issue 1: Maven Build Fails

**Problem**: `mvn clean install` fails with dependency resolution errors.

**Common Causes**:
- Outdated Maven cache
- Network connectivity issues
- Missing repository configuration

**Solutions**:

```bash
# Clear Maven cache
rm -rf ~/.m2/repository

# Re-download dependencies
mvn clean install -U

# Use explicit repository settings
mvn clean install -Dmaven.repo.local=/path/to/repo

# Check network connectivity
curl -I https://repo1.maven.org/maven2

# For GitHub Packages, ensure credentials
cat ~/.m2/settings.xml
```

**settings.xml Configuration**:
```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>YOUR_USERNAME</username>
            <password>YOUR_PERSONAL_TOKEN</password>
        </server>
    </servers>
</settings>
```

### Issue 2: Java Version Incompatibility

**Problem**: `Unsupported major.minor version` error

**Cause**: JDK version is lower than required (JDK 17+)

**Solution**:
```bash
# Check current Java version
java -version

# Verify JAVA_HOME
echo $JAVA_HOME

# Set correct JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

# Verify Maven uses correct JDK
mvn -version
```

### Issue 3: Spring Boot Dependency Conflicts

**Problem**: Dependency version conflicts or ClassNotFoundException

**Solution**:
```bash
# Show dependency tree
mvn dependency:tree

# Identify conflicts
mvn dependency:tree | grep -A 5 "CONFLICT"

# Exclude conflicting dependency
# In pom.xml:
<dependency>
    <groupId>com.microsoft.semantic-kernel</groupId>
    <artifactId>semantickernel-api</artifactId>
    <version>1.4.4-RC2</version>
    <exclusions>
        <exclusion>
            <groupId>org.conflicting.library</groupId>
            <artifactId>conflicting-artifact</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

---

## Configuration Issues

### Issue 1: Missing Environment Variables

**Problem**: `NullPointerException` when accessing configuration properties

**Cause**: Environment variables not set

**Solution**:
```bash
# Check environment variables
echo $AZURE_OPENAI_API_KEY
echo $AZURE_OPENAI_ENDPOINT

# Set variables (Linux/Mac)
export AZURE_OPENAI_API_KEY="your-api-key"
export AZURE_OPENAI_ENDPOINT="https://your-endpoint.openai.azure.com/"
export DB_URL="jdbc:mysql://localhost:3306/db"
export DB_USERNAME="user"
export DB_PASSWORD="password"

# Set variables (Windows)
set AZURE_OPENAI_API_KEY=your-api-key
set AZURE_OPENAI_ENDPOINT=https://your-endpoint.openai.azure.com/
set DB_URL=jdbc:mysql://localhost:3306/db
set DB_USERNAME=user
set DB_PASSWORD=password

# Or in application.properties
spring.ai.azure.openai.api-key=your-api-key
spring.ai.azure.openai.endpoint=https://your-endpoint.openai.azure.com/
```

### Issue 2: Invalid Configuration Properties

**Problem**: Application fails to start with configuration error

**Cause**: Invalid property values or incorrect format

**Solution**:
```properties
# Validate Azure OpenAI configuration
spring.ai.azure.openai.api-key=${AZURE_OPENAI_API_KEY}
spring.ai.azure.openai.endpoint=${AZURE_OPENAI_ENDPOINT}
spring.ai.azure.openai.chat.options.model=gpt-4  # Valid model name

# Validate database URL format
spring.datasource.url=jdbc:mysql://localhost:3306/symphony_db  # Correct format
# NOT: spring.datasource.url=localhost:3306/db  # Wrong format

# Validate Redis configuration
spring.redis.host=localhost
spring.redis.port=6379  # Numeric port
spring.redis.timeout=2000  # Numeric timeout in ms
```

### Issue 3: Application.properties Not Found

**Problem**: Spring Boot cannot find application.properties

**Solution**:
```bash
# Verify file exists
ls -la src/main/resources/application.properties

# Check file location (Spring looks in these locations)
# 1. Current directory
# 2. classpath: (src/main/resources)
# 3. classpath:config/
# 4. File system /etc/config/

# For custom location
java -jar app.jar --spring.config.location=file:/path/to/application.properties

# Or via environment variable
export SPRING_CONFIG_LOCATION=file:/path/to/application.properties
java -jar app.jar
```

---

## Runtime Errors

### Issue 1: Connection Refused to Azure OpenAI

**Problem**: `Connection refused` when calling Azure OpenAI API

**Error Message**:
```
java.net.ConnectException: Connection refused
    at java.net.PlainSocketImpl.socketConnect(Native Method)
```

**Causes and Solutions**:
```properties
# 1. Wrong endpoint URL
# Wrong: spring.ai.azure.openai.endpoint=https://myservice.openai.com/
# Correct: spring.ai.azure.openai.endpoint=https://myservice.openai.azure.com/

# 2. Invalid API key
# Verify API key is correct and not expired
spring.ai.azure.openai.api-key=${AZURE_OPENAI_API_KEY}

# 3. Network blocking
# Check firewall/proxy settings
curl -v https://myservice.openai.azure.com/
```

**Debug Code**:
```java
@Configuration
public class DebugConfig {
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate template = new RestTemplate();
        
        // Add logging interceptor
        template.getInterceptors().add((request, body, execution) -> {
            logger.info("Request: {} {}", request.getMethodValue(), request.getURI());
            return execution.execute(request, body);
        });
        
        return template;
    }
}
```

### Issue 2: Database Connection Timeout

**Problem**: `ConnectionPool timeout` when connecting to database

**Error Message**:
```
HikariPool-1 - Connection is not available, request timed out after 30000ms
```

**Solution**:
```properties
# Increase connection timeout
spring.datasource.hikari.connection-timeout=60000

# Increase idle timeout
spring.datasource.hikari.idle-timeout=600000

# Increase max lifetime
spring.datasource.hikari.max-lifetime=1800000

# Increase pool size
spring.datasource.hikari.maximum-pool-size=30

# Add connection validation
spring.datasource.hikari.connection-test-query=SELECT 1

# Verify database is running
# MySQL
mysql -h localhost -u root -p

# Check network connectivity
telnet localhost 3306
```

### Issue 3: Memory OutOfMemoryError

**Problem**: `java.lang.OutOfMemoryError: Java heap space`

**Cause**: Processing large datasets or memory leaks

**Solution**:
```bash
# Increase JVM heap size
java -Xmx4g -Xms2g -jar app.jar

# Or set in environment
export JAVA_OPTS="-Xmx4g -Xms2g"
java -jar app.jar

# For Spring Boot
java -jar -Dspring.jpa.properties.hibernate.jdbc.batch_size=20 app.jar

# Enable garbage collection logging
java -Xmx4g -Xms2g -XX:+PrintGCDetails -XX:+PrintGCDateStamps \
  -Xloggc:gc.log -jar app.jar
```

**Code-level Solution**:
```java
// Process in batches for large datasets
public Flux<Customer> streamLargeDataset() {
    return customerRepository.findAllAsFlux()
        .buffer(100)  // Process 100 at a time
        .flatMap(batch -> processBatch(batch))
        .doOnNext(customer -> logger.debug("Processed: {}", customer.getId()))
        .onErrorContinue((error, obj) -> logger.error("Error processing batch", error));
}
```

### Issue 4: JSON Processing Error

**Problem**: `JsonMappingException` or `JsonParseException`

**Error Message**:
```
com.fasterxml.jackson.databind.JsonMappingException: No constructor with single String argument
```

**Solution**:
```java
// Ensure proper JSON structure
ChatRequest request = new ChatRequest();
request.setQuery("Valid query");  // Must not be null or empty

// Validate JSON before processing
try {
    ObjectMapper mapper = new ObjectMapper();
    mapper.readValue(jsonString, ChatRequest.class);
} catch (JsonProcessingException e) {
    logger.error("Invalid JSON: {}", e.getMessage());
    // Handle error
}

// Use @JsonProperty for field mapping
public class ChatRequest {
    @JsonProperty("user_query")
    private String query;
    
    @JsonProperty("session_id")
    private String session;
}
```

### Issue 5: Authentication Failed

**Problem**: `UnauthorizedException` or `401 Unauthorized`

**Cause**: Invalid API credentials

**Solution**:
```properties
# Verify API key is correct
spring.ai.azure.openai.api-key=${AZURE_OPENAI_API_KEY}

# Check API key hasn't expired
# Azure Portal -> OpenAI resource -> Keys and endpoints

# Verify correct endpoint
spring.ai.azure.openai.endpoint=${AZURE_OPENAI_ENDPOINT}

# Check request headers
# Headers should include: Content-Type, Authorization, etc.
```

**Debug Code**:
```java
@Service
public class AuthDebugService {
    @Autowired
    private AzureOpenAiConfig config;
    
    public void debugAuth() {
        logger.info("API Key exists: {}", config.getApiKey() != null);
        logger.info("Endpoint: {}", config.getEndpoint());
        logger.info("Model: {}", config.getModelName());
    }
}
```

---

## Performance Issues

### Issue 1: Slow Response Times

**Problem**: Requests take longer than expected

**Diagnosis**:
```java
// Add timing metrics
@Service
public class PerformanceMonitor {
    
    @Aspect
    @Component
    public class TimingAspect {
        
        @Around("execution(* org.symphonykernel..*.*(..))")
        public Object measureExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
            long start = System.currentTimeMillis();
            
            try {
                return joinPoint.proceed();
            } finally {
                long duration = System.currentTimeMillis() - start;
                if (duration > 5000) {  // Log if > 5 seconds
                    logger.warn("Slow method: {} took {}ms", 
                        joinPoint.getSignature(), duration);
                }
            }
        }
    }
}
```

**Solutions**:

1. **Enable Caching**:
```properties
spring.cache.type=redis
spring.cache.redis.time-to-live=3600000

# Cache knowledge base
@Cacheable(value = "knowledge", key = "#name")
public Knowledge GetByName(String name) {
    // Load from DB
}
```

2. **Database Query Optimization**:
```java
// Use indexes
// In database:
CREATE INDEX idx_customer_id ON customers(id);
CREATE INDEX idx_order_date ON orders(order_date);

// Use SELECT only needed columns
String query = "SELECT id, name, email FROM customers WHERE active = true";

// Avoid N+1 queries
@Query("SELECT DISTINCT c FROM Customer c JOIN FETCH c.orders")
List<Customer> findAllWithOrders();
```

3. **Reduce API Calls**:
```java
// Batch requests
List<String> ids = Arrays.asList("1", "2", "3");
String query = "SELECT * FROM customers WHERE id IN (?, ?, ?)";

// Cache external API responses
@Cacheable(value = "api_cache", key = "#url", 
    cacheManager = "apiCacheManager")
public String callExternalAPI(String url) {
    // API call
}
```

### Issue 2: High Memory Usage

**Problem**: Application consumes excessive memory

**Diagnosis**:
```bash
# Monitor memory usage
jps -l  # List Java processes
jstat -gc <pid> 1000  # Show garbage collection stats every 1 second

# Generate heap dump
jmap -dump:live,format=b,file=heap.hprof <pid>

# Analyze heap dump
jhat heap.hprof
```

**Solutions**:
```java
// Avoid loading entire collections in memory
// Wrong:
List<Customer> all = customerRepository.findAll();  // Loads all!

// Right:
Page<Customer> page = customerRepository.findAll(PageRequest.of(0, 100));
Flux<Customer> stream = customerRepository.findAllAsFlux();

// Proper resource cleanup
try (BufferedReader reader = new BufferedReader(new FileReader("file.txt"))) {
    // Use resource
} catch (IOException e) {
    // Handle error
}  // Resource auto-closed
```

---

## Integration Issues

### Issue 1: REST API Integration Failed

**Problem**: REST step fails to call external API

**Diagnosis**:
```java
// Log request details
@Service
public class RestDebugService {
    public void debugRESTCall(String url, String method, Map<String, String> headers) {
        logger.info("URL: {}", url);
        logger.info("Method: {}", method);
        logger.info("Headers: {}", headers);
        
        // Test connectivity
        try {
            URL testUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) testUrl.openConnection();
            conn.setRequestMethod("GET");
            logger.info("Response Code: {}", conn.getResponseCode());
        } catch (IOException e) {
            logger.error("Connection failed: {}", e.getMessage());
        }
    }
}
```

**Solutions**:
```json
{
  "type": "REST",
  "url": "https://api.example.com/data",
  "method": "GET",
  "headers": {
    "Content-Type": "application/json",
    "Authorization": "Bearer ${contextInfo.token}",
    "User-Agent": "Symphony-Kernel/1.0"
  },
  "timeout": 30000,
  "retryCount": 3,
  "retryDelay": 1000
}
```

### Issue 2: SQL Query Execution Failed

**Problem**: SQL step fails to execute query

**Causes and Solutions**:
```java
// 1. Invalid SQL syntax
// Check SQL before execution
String query = "SELECT * FROM customers WHERE id = ?";  // Valid
// String query = "SELECT * customers WHERE id = ?";  // Missing FROM

// 2. Table not found
// Verify table exists
SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'customers';

// 3. Missing parameters
// Ensure all ? parameters are provided
Map<String, Object> params = new HashMap<>();
params.put("customerId", "123");

// 4. Connection closed
// Use connection pooling
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
```

**Debug Code**:
```java
@Service
public class SQLDebugService {
    
    @Autowired
    private DataSource dataSource;
    
    public void testConnection() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            logger.info("Database: {} {}", meta.getDatabaseProductName(), 
                meta.getDatabaseProductVersion());
            
            // List tables
            ResultSet tables = meta.getTables(null, null, "%", new String[]{"TABLE"});
            while (tables.next()) {
                logger.info("Table: {}", tables.getString("TABLE_NAME"));
            }
        } catch (SQLException e) {
            logger.error("Connection failed", e);
        }
    }
}
```

### Issue 3: GraphQL Query Failed

**Problem**: GraphQL step returns errors

**Diagnosis**:
```java
// Test GraphQL endpoint
public void testGraphQL(String endpoint, String query, Map<String, Object> variables) {
    try {
        RestTemplate restTemplate = new RestTemplate();
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);
        payload.put("variables", variables);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(
            endpoint, request, Map.class);
        
        logger.info("Response: {}", response.getBody());
        
    } catch (Exception e) {
        logger.error("GraphQL request failed", e);
    }
}
```

---

## Debugging Techniques

### Enable Debug Logging

```properties
# Enable debug for specific packages
logging.level.org.symphonykernel=DEBUG
logging.level.com.microsoft.semantickernel=DEBUG
logging.level.org.springframework.web=DEBUG
logging.level.org.springframework.data=DEBUG

# File output
logging.file.name=logs/application.log
logging.file.max-size=10MB
logging.file.max-history=10
```

### Add Debug Breakpoints

```java
@Service
public class DebugService {
    
    @Autowired
    private Agent agent;
    
    public ChatResponse debugProcess(ChatRequest request) {
        // Set breakpoint here
        logger.debug("Request: {}", request.getQuery());
        
        ChatResponse response = agent.process(request);
        
        // Set breakpoint here
        logger.debug("Response: {}", response.getMessage());
        
        return response;
    }
}
```

### Unit Test for Debugging

```java
@ExtendWith(SpringExtension.class)
@SpringBootTest
public class DebugTest {
    
    @Autowired
    private Agent agent;
    
    @Test
    void debugRequest() {
        ChatRequest request = new ChatRequest();
        request.setQuery("Debug query");
        request.setUser("test_user");
        
        // Set breakpoint and step through
        ChatResponse response = agent.process(request);
        
        assertNotNull(response);
        assertEquals("200", response.getStatusCode());
    }
}
```

---

## FAQ

### Q1: How do I upgrade to a newer version?

**A**: 
```bash
# Update pom.xml with new version
# Or use Maven plugin
mvn versions:set -DnewVersion=0.5.0-SNAPSHOT

# Rebuild
mvn clean install
```

### Q2: How do I enable API documentation with Swagger?

**A**:
```xml
<!-- Add to pom.xml -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.1.0</version>
</dependency>
```

Then access at `http://localhost:8080/swagger-ui.html`

### Q3: How do I implement custom authentication?

**A**:
```java
@Component
public class CustomAuthFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
            HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        
        String token = request.getHeader("Authorization");
        if (token != null && validateToken(token)) {
            // Token is valid, proceed
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
```

### Q4: How do I monitor application health?

**A**:
```properties
# Add Actuator
management.endpoints.web.exposure.include=health,metrics,prometheus

# Access health endpoint
curl http://localhost:8080/actuator/health
```

### Q5: How do I implement distributed tracing?

**A**:
```xml
<!-- Add Spring Cloud Sleuth -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
</dependency>
```

Trace ID will be automatically added to logs.

---

**Document Version**: 1.0  
**Last Updated**: February 2026
