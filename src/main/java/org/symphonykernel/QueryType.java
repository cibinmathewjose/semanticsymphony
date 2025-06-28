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
    SYMPHNOY,   
    /** Represents a GraphQL query. */
    GRAPHQL,
    /** Represents a plugin-based query. */
    PLUGIN,   
    /** Represents a text-based query for file */
    FILE,
     /** Represents a text-based query for sharepoint */
     SHAREPOINT
}
