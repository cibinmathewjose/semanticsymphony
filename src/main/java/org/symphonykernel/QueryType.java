package org.symphonykernel;

/**
 * Enum representing different types of queries.
 */
public enum QueryType {
    /** Represents an unknown query type. */
    UNKNOWN ,
    /** Represents an SQL query. */
    SQL, 
    /** Represents a REST API query. */
    REST,     
    /** Represents a Symphony-specific query. */
    SYMPHONY,   
    /** Represents a GraphQL query. */
    GRAPHQL,
    /** Represents a plugin-based query. */
    PLUGIN,   
    /** Represents a text-based query for file */
    TOOL,   
    /** Represents velocity template engine */
    VELOCITY,
    /** Represents a text-based query for file */
    FILE,
     /** Represents a text-based query for sharepoint */
     SHAREPOINT,
    /** Represents an SAP RFC */
     RFC,
    /** Represents an agentic workflow where the LLM dynamically plans and executes steps */
     AGENTIC,   
    /** Represents a dynamic database introspection and query step */
     DATABASE,    
    /** Represents a human-in-the-loop step that pauses for user input */
     HUMANLOOP,
    /** Represents an MCP step for external MCP server/tool execution */
     MCP,
    /** Delegates a text turn to an outbound Agent2Agent endpoint. */
    A2A,
    /** Searches an existing Azure AI Search index. */
    AZURE_AI_SEARCH,    
     /** Represents a web search step to find information on the internet */
     WEBSEARCH,
    /** Represents an authentication step to acquire tokens for API calls */
     AUTH,
    /** Represents an email sending step */
    EMAIL
  
}
