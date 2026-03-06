package org.symphonykernel.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.symphonykernel.Knowledge;
import org.symphonykernel.core.IknowledgeBase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

/**
 * Registry that discovers all Symphony knowledge entries and exposes them
 * as MCP-compatible tool descriptors. This enables any agentic framework
 * to discover and invoke Symphony steps via the MCP protocol.
 */
@Component
public class MCPToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(MCPToolRegistry.class);

    @Autowired
    private IknowledgeBase knowledgeBase;

    @Autowired
    private ObjectMapper objectMapper;

    /** Internal registry mapping tool names to descriptors. */
    private final ConcurrentHashMap<String, MCPToolDescriptor> toolRegistry = new ConcurrentHashMap<>();

    /** Initializes the tool registry on application startup. */
    @PostConstruct
    public void init() {
        refreshRegistry();
    }

    /**
     * Scans all knowledge entries and builds MCP tool descriptors from them.
     */
    public void refreshRegistry() {
        toolRegistry.clear();
        Map<String, String> descriptions = knowledgeBase.getAllKnowledgeDescriptions();
        if (descriptions == null || descriptions.isEmpty()) {
            logger.warn("No knowledge descriptions found for MCP tool registry");
            return;
        }

        for (Map.Entry<String, String> entry : descriptions.entrySet()) {
            String name = entry.getKey();
            String description = entry.getValue();
            try {
                Knowledge kb = knowledgeBase.GetByName(name);
                if (kb != null) {
                    MCPToolDescriptor descriptor = buildToolDescriptor(kb, description);
                    toolRegistry.put(name, descriptor);
                }
            } catch (Exception e) {
                logger.warn("Failed to register tool for knowledge '{}': {}", name, e.getMessage());
            }
        }
        logger.info("MCP Tool Registry initialized with {} tools", toolRegistry.size());
    }

    /**
     * Returns all registered tool descriptors.
     *
     * @return list of all tool descriptors
     */
    public List<MCPToolDescriptor> listTools() {
        return new ArrayList<>(toolRegistry.values());
    }

    /**
     * Returns a specific tool descriptor by name.
     *
     * @param name the tool name
     * @return the tool descriptor, or null if not found
     */
    public MCPToolDescriptor getTool(String name) {
        return toolRegistry.get(name);
    }

    /**
     * Returns the registry as an unmodifiable map.
     *
     * @return unmodifiable map of tool names to descriptors
     */
    public Map<String, MCPToolDescriptor> getRegistry() {
        return Collections.unmodifiableMap(toolRegistry);
    }

    /**
     * Registers an external (e.g. MCP client-discovered) tool into the registry.
     *
     * @param descriptor the tool descriptor to register
     */
    public void registerExternalTool(MCPToolDescriptor descriptor) {
        toolRegistry.put(descriptor.getName(), descriptor);
        logger.info("Registered external MCP tool: {}", descriptor.getName());
    }

    private MCPToolDescriptor buildToolDescriptor(Knowledge kb, String description) {
        Map<String, Object> inputSchema = parseInputSchema(kb.getParams());
        String queryType = kb.getType() != null ? kb.getType().name() : "UNKNOWN";
        return new MCPToolDescriptor(kb.getName(), description, inputSchema, queryType);
    }

    private Map<String, Object> parseInputSchema(String params) {
        if (params == null || params.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            JsonNode node = objectMapper.readTree(params);
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            if (node.isArray() && node.size() > 0) {
                node = node.get(0);
            }
            if (node.isObject()) {
                Map<String, Object> properties = objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
                schema.put("properties", properties);
            }
            return schema;
        } catch (Exception e) {
            logger.debug("Could not parse params as JSON schema for tool: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
