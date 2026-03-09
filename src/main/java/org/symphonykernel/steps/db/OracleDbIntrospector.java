package org.symphonykernel.steps.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Oracle-specific schema introspector using Oracle data dictionary views
 * ({@code ALL_TABLES}, {@code ALL_TAB_COLUMNS}, {@code ALL_CONSTRAINTS}, etc.)
 * for fast, targeted introspection instead of JDBC {@code DatabaseMetaData}.
 */
public class OracleDbIntrospector implements DbIntrospector {

    private static final Logger logger = LoggerFactory.getLogger(OracleDbIntrospector.class);

    @Override
    public boolean supports(String databaseProductName) {
        return databaseProductName != null
                && databaseProductName.toUpperCase().contains("ORACLE");
    }

    @Override
    public List<String> listTableNames(Connection connection, List<String> schemas,
                                        String tableType) throws SQLException {
        List<String> names = new ArrayList<>();
        String view = "VIEW".equalsIgnoreCase(tableType) ? "ALL_VIEWS" : "ALL_TABLES";
        String ownerCol = "OWNER";
        String nameCol = "VIEW".equalsIgnoreCase(tableType) ? "VIEW_NAME" : "TABLE_NAME";

        if (schemas.isEmpty()) {
            // Use current schema
            String sql = "SELECT " + ownerCol + ", " + nameCol + " FROM " + view
                    + " WHERE " + ownerCol + " = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA')"
                    + " ORDER BY " + nameCol;
            try (PreparedStatement stmt = connection.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(ownerCol) + "." + rs.getString(nameCol));
                }
            }
        } else {
            String placeholders = String.join(",", schemas.stream().map(s -> "?").toArray(String[]::new));
            String sql = "SELECT " + ownerCol + ", " + nameCol + " FROM " + view
                    + " WHERE UPPER(" + ownerCol + ") IN (" + placeholders + ")"
                    + " ORDER BY " + ownerCol + ", " + nameCol;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                for (int i = 0; i < schemas.size(); i++) {
                    stmt.setString(i + 1, schemas.get(i).toUpperCase());
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        names.add(rs.getString(ownerCol) + "." + rs.getString(nameCol));
                    }
                }
            }
        }
        return names;
    }

    @Override
    public String introspectSchema(Connection connection, List<String> schemas,
                                    List<String> tables, List<String> views) throws SQLException {
        StringBuilder sb = new StringBuilder();
        if (!tables.isEmpty()) {
            for (String tableName : tables) {
                appendTableDetail(connection, schemas, tableName, "TABLE", sb);
            }
        }
        if (!views.isEmpty()) {
            for (String viewName : views) {
                appendTableDetail(connection, schemas, viewName, "VIEW", sb);
            }
        }
        return sb.toString();
    }

    /**
     * Appends full metadata for a single table/view: columns, PK, FKs, indexes.
     */
    private void appendTableDetail(Connection connection, List<String> schemas,
                                    String qualifiedName, String objectType,
                                    StringBuilder sb) throws SQLException {
        String owner;
        String name;
        if (qualifiedName.contains(".")) {
            int dot = qualifiedName.lastIndexOf('.');
            owner = qualifiedName.substring(0, dot).toUpperCase();
            name = qualifiedName.substring(dot + 1).toUpperCase();
        } else {
            owner = null;
            name = qualifiedName.toUpperCase();
        }

        // Resolve the actual owner if not specified
        String resolvedOwner = owner;
        if (resolvedOwner == null) {
            resolvedOwner = resolveOwner(connection, schemas, name, objectType);
            if (resolvedOwner == null) {
                logger.warn("Could not resolve owner for {} '{}'", objectType, qualifiedName);
                return;
            }
        }

        String fullName = resolvedOwner + "." + name;
        sb.append("\n").append(objectType).append(": ").append(fullName).append("\n");

        appendColumns(connection, resolvedOwner, name, sb);
        appendPrimaryKey(connection, resolvedOwner, name, sb);
        appendForeignKeys(connection, resolvedOwner, name, sb);
        if ("TABLE".equals(objectType)) {
            appendIndexes(connection, resolvedOwner, name, sb);
        }
    }

    private String resolveOwner(Connection connection, List<String> schemas,
                                 String tableName, String objectType) throws SQLException {
        String view = "VIEW".equalsIgnoreCase(objectType) ? "ALL_VIEWS" : "ALL_TABLES";
        String nameCol = "VIEW".equalsIgnoreCase(objectType) ? "VIEW_NAME" : "TABLE_NAME";

        if (!schemas.isEmpty()) {
            String placeholders = String.join(",", schemas.stream().map(s -> "?").toArray(String[]::new));
            String sql = "SELECT OWNER FROM " + view
                    + " WHERE UPPER(" + nameCol + ") = ? AND UPPER(OWNER) IN (" + placeholders + ")"
                    + " FETCH FIRST 1 ROWS ONLY";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, tableName);
                for (int i = 0; i < schemas.size(); i++) {
                    stmt.setString(i + 2, schemas.get(i).toUpperCase());
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("OWNER");
                    }
                }
            }
        }
        // Fallback: current schema
        String sql = "SELECT OWNER FROM " + view
                + " WHERE UPPER(" + nameCol + ") = ?"
                + " AND OWNER = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA')"
                + " FETCH FIRST 1 ROWS ONLY";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("OWNER");
                }
            }
        }
        return null;
    }

    private void appendColumns(Connection connection, String owner, String tableName,
                                StringBuilder sb) throws SQLException {
        sb.append("  Columns:\n");
        String sql = "SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, DATA_PRECISION, DATA_SCALE, NULLABLE"
                + " FROM ALL_TAB_COLUMNS"
                + " WHERE OWNER = ? AND TABLE_NAME = ?"
                + " ORDER BY COLUMN_ID";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, owner);
            stmt.setString(2, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    String dataType = rs.getString("DATA_TYPE");
                    String size = formatColumnSize(rs);
                    String nullable = "Y".equals(rs.getString("NULLABLE")) ? "NULL" : "NOT NULL";
                    sb.append("    - ").append(colName).append(" ").append(dataType)
                      .append(size).append(" ").append(nullable).append("\n");
                }
            }
        }
    }

    private String formatColumnSize(ResultSet rs) throws SQLException {
        String dataType = rs.getString("DATA_TYPE");
        int precision = rs.getInt("DATA_PRECISION");
        int scale = rs.getInt("DATA_SCALE");
        int length = rs.getInt("DATA_LENGTH");

        if (dataType.contains("CHAR") || dataType.contains("RAW")) {
            return "(" + length + ")";
        } else if (precision > 0) {
            return scale > 0 ? "(" + precision + "," + scale + ")" : "(" + precision + ")";
        }
        return "";
    }

    private void appendPrimaryKey(Connection connection, String owner, String tableName,
                                   StringBuilder sb) throws SQLException {
        String sql = "SELECT cc.COLUMN_NAME"
                + " FROM ALL_CONSTRAINTS c"
                + " JOIN ALL_CONS_COLUMNS cc ON c.OWNER = cc.OWNER"
                + "   AND c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME"
                + " WHERE c.OWNER = ? AND c.TABLE_NAME = ? AND c.CONSTRAINT_TYPE = 'P'"
                + " ORDER BY cc.POSITION";
        List<String> pkCols = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, owner);
            stmt.setString(2, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    pkCols.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        if (!pkCols.isEmpty()) {
            sb.append("  Primary Key: ").append(String.join(", ", pkCols)).append("\n");
        }
    }

    private void appendForeignKeys(Connection connection, String owner, String tableName,
                                    StringBuilder sb) throws SQLException {
        String sql = "SELECT cc.COLUMN_NAME AS FK_COL,"
                + " rc.OWNER AS REF_OWNER, rc.TABLE_NAME AS REF_TABLE,"
                + " rcc.COLUMN_NAME AS REF_COL"
                + " FROM ALL_CONSTRAINTS c"
                + " JOIN ALL_CONS_COLUMNS cc ON c.OWNER = cc.OWNER"
                + "   AND c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME"
                + " JOIN ALL_CONSTRAINTS rc ON c.R_OWNER = rc.OWNER"
                + "   AND c.R_CONSTRAINT_NAME = rc.CONSTRAINT_NAME"
                + " JOIN ALL_CONS_COLUMNS rcc ON rc.OWNER = rcc.OWNER"
                + "   AND rc.CONSTRAINT_NAME = rcc.CONSTRAINT_NAME"
                + "   AND cc.POSITION = rcc.POSITION"
                + " WHERE c.OWNER = ? AND c.TABLE_NAME = ? AND c.CONSTRAINT_TYPE = 'R'"
                + " ORDER BY c.CONSTRAINT_NAME, cc.POSITION";
        boolean hasFK = false;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, owner);
            stmt.setString(2, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    if (!hasFK) {
                        sb.append("  Foreign Keys:\n");
                        hasFK = true;
                    }
                    String fkCol = rs.getString("FK_COL");
                    String refOwner = rs.getString("REF_OWNER");
                    String refTable = rs.getString("REF_TABLE");
                    String refCol = rs.getString("REF_COL");
                    sb.append("    - ").append(fkCol)
                      .append(" -> ").append(refOwner).append(".").append(refTable)
                      .append("(").append(refCol).append(")\n");
                }
            }
        }
    }

    private void appendIndexes(Connection connection, String owner, String tableName,
                                StringBuilder sb) throws SQLException {
        String sql = "SELECT i.INDEX_NAME, ic.COLUMN_NAME, i.UNIQUENESS"
                + " FROM ALL_INDEXES i"
                + " JOIN ALL_IND_COLUMNS ic ON i.OWNER = ic.INDEX_OWNER"
                + "   AND i.INDEX_NAME = ic.INDEX_NAME"
                + " WHERE i.TABLE_OWNER = ? AND i.TABLE_NAME = ?"
                + " ORDER BY i.INDEX_NAME, ic.COLUMN_POSITION";
        Map<String, List<String>> indexMap = new LinkedHashMap<>();
        Map<String, Boolean> uniqueMap = new LinkedHashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, owner);
            stmt.setString(2, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    String colName = rs.getString("COLUMN_NAME");
                    boolean unique = "UNIQUE".equals(rs.getString("UNIQUENESS"));
                    indexMap.computeIfAbsent(indexName, k -> new ArrayList<>()).add(colName);
                    uniqueMap.putIfAbsent(indexName, unique);
                }
            }
        }
        if (!indexMap.isEmpty()) {
            sb.append("  Indexes:\n");
            for (Map.Entry<String, List<String>> entry : indexMap.entrySet()) {
                String idxName = entry.getKey();
                String cols = String.join(", ", entry.getValue());
                String uniqueLabel = Boolean.TRUE.equals(uniqueMap.get(idxName)) ? " UNIQUE" : "";
                sb.append("    - ").append(idxName).append(" (").append(cols).append(")").append(uniqueLabel).append("\n");
            }
        }
    }
}
