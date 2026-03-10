package org.symphonykernel.steps.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Strategy interface for database schema introspection.
 * Implementations use database-specific catalog queries for optimal performance.
 */
public interface DbIntrospector {

    /**
     * Returns true if this introspector supports the given database product.
     *
     * @param databaseProductName the value from {@code DatabaseMetaData.getDatabaseProductName()}
     */
    boolean supports(String databaseProductName);

    /**
     * Lists all table or view names (schema-qualified) for the given schemas.
     *
     * @param connection   the JDBC connection
     * @param schemas      schema names to scan (empty means all discoverable schemas)
     * @param tableType    "TABLE" or "VIEW"
     * @return list of schema-qualified names, e.g. ["dbo.Customers", "dbo.Orders"]
     */
    List<String> listTableNames(Connection connection, List<String> schemas, String tableType) throws SQLException;

    /**
     * Builds a human-readable schema description for only the specified tables and views,
     * including columns, primary keys, foreign keys, and indexes.
     *
     * @param connection the JDBC connection
     * @param schemas    schema names to scan
     * @param tables     table names to introspect (may include schema prefix)
     * @param views      view names to introspect (may include schema prefix)
     * @return schema description string for LLM consumption
     */
    String introspectSchema(Connection connection, List<String> schemas,
                            List<String> tables, List<String> views) throws SQLException;
}
