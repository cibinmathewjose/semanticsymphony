package org.symphonykernel.steps.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback introspector that uses standard JDBC {@link DatabaseMetaData} calls.
 * Works with any JDBC-compliant database but may be slower than database-specific
 * implementations.
 */
public class JdbcDbIntrospector implements DbIntrospector {

    private static final Logger logger = LoggerFactory.getLogger(JdbcDbIntrospector.class);

    @Override
    public boolean supports(String databaseProductName) {
        return true; // universal fallback
    }

    @Override
    public List<String> listTableNames(Connection connection, List<String> schemas,
                                        String tableType) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        List<String> schemaList = schemas.isEmpty() ? discoverSchemas(metaData) : schemas;
        List<String> names = new ArrayList<>();
        for (String schema : schemaList) {
            try (ResultSet rs = metaData.getTables(null, schema, "%", new String[]{tableType})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String tableSchema = rs.getString("TABLE_SCHEM");
                    String fullName = (tableSchema != null ? tableSchema + "." : "") + tableName;
                    names.add(fullName);
                }
            }
        }
        return names;
    }

    @Override
    public String introspectSchema(Connection connection, List<String> schemas,
                                    List<String> tables, List<String> views) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        StringBuilder sb = new StringBuilder();
        List<String> schemaList = schemas.isEmpty() ? discoverSchemas(metaData) : schemas;

        for (String schema : schemaList) {
            appendTablesMetadata(metaData, schema, tables, "TABLE", sb);
            appendTablesMetadata(metaData, schema, views, "VIEW", sb);
        }
        return sb.toString();
    }

    private List<String> discoverSchemas(DatabaseMetaData metaData) throws SQLException {
        List<String> discovered = new ArrayList<>();
        try (ResultSet rs = metaData.getSchemas()) {
            while (rs.next()) {
                discovered.add(rs.getString("TABLE_SCHEM"));
            }
        }
        if (discovered.isEmpty()) {
            discovered.add(null);
        }
        return discovered;
    }

    private void appendTablesMetadata(DatabaseMetaData metaData, String schema,
                                       List<String> filterNames, String tableType,
                                       StringBuilder sb) throws SQLException {
        if (filterNames.isEmpty()) {
            return;
        }
        for (String filterName : filterNames) {
            String tableNamePattern = filterName.contains(".")
                    ? filterName.substring(filterName.lastIndexOf('.') + 1)
                    : filterName;
            try (ResultSet tablesRs = metaData.getTables(null, schema, tableNamePattern, new String[]{tableType})) {
                while (tablesRs.next()) {
                    String tableName = tablesRs.getString("TABLE_NAME");
                    String tableSchema = tablesRs.getString("TABLE_SCHEM");
                    String fullName = (tableSchema != null ? tableSchema + "." : "") + tableName;

                    sb.append("\n").append(tableType).append(": ").append(fullName).append("\n");

                    appendColumns(metaData, tableSchema, tableName, sb);
                    appendPrimaryKeys(metaData, tableSchema, tableName, sb);
                    appendForeignKeys(metaData, tableSchema, tableName, sb);
                    appendIndexes(metaData, tableSchema, tableName, sb);
                }
            }
        }
    }

    private void appendColumns(DatabaseMetaData metaData, String schema,
                                String tableName, StringBuilder sb) throws SQLException {
        sb.append("  Columns:\n");
        try (ResultSet cols = metaData.getColumns(null, schema, tableName, "%")) {
            while (cols.next()) {
                String colName = cols.getString("COLUMN_NAME");
                String typeName = cols.getString("TYPE_NAME");
                int size = cols.getInt("COLUMN_SIZE");
                String nullable = "YES".equals(cols.getString("IS_NULLABLE")) ? "NULL" : "NOT NULL";
                sb.append("    - ").append(colName)
                  .append(" ").append(typeName)
                  .append("(").append(size).append(")")
                  .append(" ").append(nullable).append("\n");
            }
        }
    }

    private void appendPrimaryKeys(DatabaseMetaData metaData, String schema,
                                    String tableName, StringBuilder sb) throws SQLException {
        List<String> pkCols = new ArrayList<>();
        try (ResultSet pks = metaData.getPrimaryKeys(null, schema, tableName)) {
            while (pks.next()) {
                pkCols.add(pks.getString("COLUMN_NAME"));
            }
        }
        if (!pkCols.isEmpty()) {
            sb.append("  Primary Key: ").append(String.join(", ", pkCols)).append("\n");
        }
    }

    private void appendForeignKeys(DatabaseMetaData metaData, String schema,
                                    String tableName, StringBuilder sb) throws SQLException {
        try (ResultSet fks = metaData.getImportedKeys(null, schema, tableName)) {
            boolean hasFK = false;
            while (fks.next()) {
                if (!hasFK) {
                    sb.append("  Foreign Keys:\n");
                    hasFK = true;
                }
                String fkCol = fks.getString("FKCOLUMN_NAME");
                String pkTable = fks.getString("PKTABLE_NAME");
                String pkSchema = fks.getString("PKTABLE_SCHEM");
                String pkCol = fks.getString("PKCOLUMN_NAME");
                String refTable = (pkSchema != null ? pkSchema + "." : "") + pkTable;
                sb.append("    - ").append(fkCol)
                  .append(" -> ").append(refTable).append("(").append(pkCol).append(")\n");
            }
        }
    }

    private void appendIndexes(DatabaseMetaData metaData, String schema,
                                String tableName, StringBuilder sb) throws SQLException {
        try (ResultSet idxs = metaData.getIndexInfo(null, schema, tableName, false, true)) {
            String currentIndex = null;
            List<String> indexCols = new ArrayList<>();
            boolean headerWritten = false;

            while (idxs.next()) {
                String indexName = idxs.getString("INDEX_NAME");
                if (indexName == null) {
                    continue;
                }
                String colName = idxs.getString("COLUMN_NAME");
                boolean nonUnique = idxs.getBoolean("NON_UNIQUE");

                if (!indexName.equals(currentIndex)) {
                    if (currentIndex != null) {
                        if (!headerWritten) {
                            sb.append("  Indexes:\n");
                            headerWritten = true;
                        }
                        sb.append("    - ").append(currentIndex)
                          .append(" (").append(String.join(", ", indexCols)).append(")\n");
                    }
                    currentIndex = indexName;
                    indexCols = new ArrayList<>();
                }
                if (colName != null) {
                    indexCols.add(colName + (nonUnique ? "" : " UNIQUE"));
                }
            }
            if (currentIndex != null) {
                if (!headerWritten) {
                    sb.append("  Indexes:\n");
                }
                sb.append("    - ").append(currentIndex)
                  .append(" (").append(String.join(", ", indexCols)).append(")\n");
            }
        }
    }
}
