package org.symphonykernel.mcp;

import java.util.Map;

/**
 * Describes a tool that can be exposed via MCP or used internally by the agentic planner.
 * Maps Symphony knowledge/steps to a standard tool description format.
 */
public class MCPToolDescriptor {

    private String name;
    private String description;
    private Map<String, Object> inputSchema;
    private String queryType;

    /** Default constructor. */
    public MCPToolDescriptor() {
    }

    /**
     * Constructs an MCPToolDescriptor with all fields.
     *
     * @param name the tool name
     * @param description the tool description
     * @param inputSchema the input JSON schema
     * @param queryType the query type
     */
    public MCPToolDescriptor(String name, String description, Map<String, Object> inputSchema, String queryType) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.queryType = queryType;
    }

    /** @return the tool name */
    public String getName() {
        return name;
    }

    /** @param name the tool name to set */
    public void setName(String name) {
        this.name = name;
    }

    /** @return the tool description */
    public String getDescription() {
        return description;
    }

    /** @param description the tool description to set */
    public void setDescription(String description) {
        this.description = description;
    }

    /** @return the input JSON schema */
    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }

    /** @param inputSchema the input schema to set */
    public void setInputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = inputSchema;
    }

    /** @return the query type */
    public String getQueryType() {
        return queryType;
    }

    /** @param queryType the query type to set */
    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }

    @Override
    public String toString() {
        return "MCPToolDescriptor{name='" + name + "', description='" + description + "', queryType='" + queryType + "'}";
    }
}
