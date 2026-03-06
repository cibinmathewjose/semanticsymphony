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

    public MCPToolDescriptor() {
    }

    public MCPToolDescriptor(String name, String description, Map<String, Object> inputSchema, String queryType) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.queryType = queryType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = inputSchema;
    }

    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }

    @Override
    public String toString() {
        return "MCPToolDescriptor{name='" + name + "', description='" + description + "', queryType='" + queryType + "'}";
    }
}
