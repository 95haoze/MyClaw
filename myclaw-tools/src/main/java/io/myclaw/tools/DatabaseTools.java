package io.myclaw.tools;

import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Structured JDBC tools that reuse server-managed DataSource instances. */
public final class DatabaseTools {
    public record Profile(DataSource dataSource, boolean allowWrite, int maxRows, int timeoutSeconds) {
        public Profile {
            if (dataSource == null) throw new IllegalArgumentException("dataSource is required");
            maxRows = Math.max(1, Math.min(maxRows, 1000));
            timeoutSeconds = Math.max(1, Math.min(timeoutSeconds, 120));
        }
    }

    private DatabaseTools() {}

    public static Tool schema(Map<String, Profile> profiles) {
        return tool("db_schema", "Inspect database tables, columns, primary keys, and indexes through a server-managed DataSource. Connection credentials are never exposed.",
                JsonSchema.object().string("profile", "DataSource profile; optional when only one exists", false)
                        .string("schema", "Optional schema/catalog pattern", false)
                        .string("table", "Optional table name pattern", false)
                        .integer("maxTables", "Maximum tables, 1-200; defaults to 50", false).build(),
                (args, context) -> inspectSchema(select(profiles, args), args));
    }

    public static Tool query(Map<String, Profile> profiles) {
        return tool("db_query", "Execute one read-only parameterized SQL query. Only SELECT, WITH, SHOW, DESCRIBE, DESC, and EXPLAIN are accepted.",
                JsonSchema.object().string("profile", "DataSource profile; optional when only one exists", false)
                        .string("sql", "One read-only SQL statement using ? placeholders")
                        .raw("parameters", JsonSchema.object().arrayOf("values", "", JsonSchema.stringSchema(), false).build().path("properties").path("values"), false)
                        .integer("maxRows", "Maximum returned rows; capped by backend profile", false).build(),
                (args, context) -> runQuery(select(profiles, args), ToolSupport.requiredString(args, "sql"), args));
    }

    public static Tool execute(Map<String, Profile> profiles) {
        return tool("db_execute", "Execute one parameterized INSERT, UPDATE, DELETE, or MERGE in a transaction. The selected backend profile must explicitly allow writes.",
                JsonSchema.object().string("profile", "Writable DataSource profile; optional when only one exists", false)
                        .string("sql", "One DML statement using ? placeholders")
                        .raw("parameters", JsonSchema.object().arrayOf("values", "", JsonSchema.stringSchema(), false).build().path("properties").path("values"), false)
                        .build(),
                (args, context) -> runExecute(select(profiles, args), ToolSupport.requiredString(args, "sql"), args));
    }

    private static String inspectSchema(Profile profile, JsonNode args) throws Exception {
        int maxTables = Math.max(1, Math.min(200, ToolSupport.optionalInt(args, "maxTables", 50)));
        String schema = ToolSupport.optionalString(args, "schema", null);
        String tablePattern = ToolSupport.optionalString(args, "table", "%");
        StringBuilder out = new StringBuilder();
        try (Connection connection = profile.dataSource().getConnection()) {
            connection.setReadOnly(true);
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = schema == null ? connection.getCatalog() : schema;
            int tables = 0;
            try (ResultSet result = metadata.getTables(catalog, schema, tablePattern, new String[]{"TABLE", "VIEW"})) {
                while (result.next() && tables++ < maxTables) {
                    String table = result.getString("TABLE_NAME");
                    String tableSchema = result.getString("TABLE_SCHEM");
                    if (!out.isEmpty()) out.append('\n');
                    out.append(result.getString("TABLE_TYPE")).append(' ')
                            .append(tableSchema == null ? "" : tableSchema + ".").append(table).append('\n');
                    Map<String, String> primaryKeys = new LinkedHashMap<>();
                    try (ResultSet keys = metadata.getPrimaryKeys(catalog, tableSchema, table)) {
                        while (keys.next()) primaryKeys.put(keys.getString("COLUMN_NAME"), "PK");
                    }
                    try (ResultSet columns = metadata.getColumns(catalog, tableSchema, table, "%")) {
                        while (columns.next()) {
                            String column = columns.getString("COLUMN_NAME");
                            out.append("  - ").append(column).append(' ').append(columns.getString("TYPE_NAME"));
                            int size = columns.getInt("COLUMN_SIZE");
                            if (size > 0) out.append('(').append(size).append(')');
                            if (columns.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls) out.append(" NOT NULL");
                            if (primaryKeys.containsKey(column)) out.append(" PRIMARY KEY");
                            out.append('\n');
                        }
                    }
                    try (ResultSet indexes = metadata.getIndexInfo(catalog, tableSchema, table, false, false)) {
                        while (indexes.next()) {
                            String index = indexes.getString("INDEX_NAME");
                            String column = indexes.getString("COLUMN_NAME");
                            if (index != null && column != null) out.append("    INDEX ").append(index).append(" (").append(column).append(")\n");
                        }
                    }
                    if (out.length() > 50_000) return ToolSupport.truncate(out.toString(), 50_000);
                }
            }
        }
        return out.isEmpty() ? "No tables matched." : out.toString().stripTrailing();
    }

    private static String runQuery(Profile profile, String sql, JsonNode args) throws Exception {
        SqlSafety.requireReadOnly(sql);
        int requested = ToolSupport.optionalInt(args, "maxRows", profile.maxRows());
        int maxRows = Math.max(1, Math.min(profile.maxRows(), requested));
        try (Connection connection = profile.dataSource().getConnection()) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(profile.timeoutSeconds());
                statement.setMaxRows(maxRows + 1);
                bind(statement, args == null ? null : args.get("parameters"));
                try (ResultSet result = statement.executeQuery()) { return formatRows(result, maxRows); }
            }
        }
    }

    private static String runExecute(Profile profile, String sql, JsonNode args) throws Exception {
        if (!profile.allowWrite()) throw new SecurityException("Database profile is read-only; enable allow-write in backend configuration");
        SqlSafety.requireDml(sql);
        try (Connection connection = profile.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(profile.timeoutSeconds());
                bind(statement, args == null ? null : args.get("parameters"));
                int affected = statement.executeUpdate();
                connection.commit();
                return "Committed successfully; affected rows: " + affected;
            } catch (Exception exception) {
                try { connection.rollback(); } catch (SQLException rollback) { exception.addSuppressed(rollback); }
                throw exception;
            } finally { try { connection.setAutoCommit(true); } catch (SQLException ignored) { } }
        }
    }

    private static void bind(PreparedStatement statement, JsonNode parameters) throws SQLException {
        if (parameters == null || parameters.isNull()) return;
        if (!parameters.isArray()) throw new IllegalArgumentException("parameters must be an array");
        int index = 1;
        for (JsonNode value : parameters) {
            if (value == null || value.isNull()) statement.setObject(index++, null);
            else if (value.isBoolean()) statement.setBoolean(index++, value.asBoolean());
            else if (value.isIntegralNumber()) statement.setLong(index++, value.asLong());
            else if (value.isFloatingPointNumber()) statement.setDouble(index++, value.asDouble());
            else statement.setString(index++, value.asString());
        }
    }

    private static String formatRows(ResultSet result, int maxRows) throws SQLException {
        ResultSetMetaData metadata = result.getMetaData();
        int columns = metadata.getColumnCount();
        StringBuilder out = new StringBuilder();
        for (int column = 1; column <= columns; column++) {
            if (column > 1) out.append('\t'); out.append(metadata.getColumnLabel(column));
        }
        out.append('\n');
        int rows = 0;
        boolean truncated = false;
        while (result.next()) {
            if (rows++ >= maxRows) { truncated = true; break; }
            for (int column = 1; column <= columns; column++) {
                if (column > 1) out.append('\t');
                Object value = result.getObject(column);
                out.append(value == null ? "NULL" : ToolSupport.truncate(String.valueOf(value).replace('\n', ' '), 2_000));
            }
            out.append('\n');
            if (out.length() > 50_000) { truncated = true; break; }
        }
        out.append("Rows: ").append(Math.min(rows, maxRows));
        if (truncated) out.append(" (truncated)");
        return out.toString();
    }

    private static Profile select(Map<String, Profile> profiles, JsonNode args) {
        if (profiles == null || profiles.isEmpty()) throw new IllegalStateException("No database DataSource is configured");
        String requested = ToolSupport.optionalString(args, "profile", "");
        if (requested.isBlank() && profiles.size() == 1) return profiles.values().iterator().next();
        Profile profile = profiles.get(requested);
        if (profile == null) throw new IllegalArgumentException("Unknown database profile '" + requested + "'. Available: " + profiles.keySet());
        return profile;
    }

    static final class SqlSafety {
        private SqlSafety() {}
        static void requireReadOnly(String sql) { require(sql, List.of("SELECT", "WITH", "SHOW", "DESCRIBE", "DESC", "EXPLAIN"), "read-only query"); }
        static void requireDml(String sql) { require(sql, List.of("INSERT", "UPDATE", "DELETE", "MERGE"), "DML statement"); }
        private static void require(String sql, List<String> allowed, String label) {
            if (sql == null || sql.isBlank()) throw new IllegalArgumentException("sql must not be blank");
            String normalized = stripCommentsAndLiterals(sql);
            String first = normalized.stripLeading().split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
            if (!allowed.contains(first)) throw new SecurityException("Only one " + label + " is allowed; found " + first);
            if (normalized.indexOf(';') >= 0) throw new SecurityException("Multiple SQL statements and semicolons are not allowed");
            String upper = normalized.toUpperCase(Locale.ROOT);
            for (String forbidden : List.of(" DROP ", " ALTER ", " CREATE ", " TRUNCATE ", " GRANT ", " REVOKE ", " COMMIT ", " ROLLBACK ", " CALL ", " INTO OUTFILE ", " FOR UPDATE ", " LOCK IN SHARE MODE "))
                if ((" " + upper + " ").contains(forbidden)) throw new SecurityException("Forbidden SQL keyword: " + forbidden.strip());
        }
        private static String stripCommentsAndLiterals(String sql) {
            StringBuilder out = new StringBuilder(sql.length());
            boolean single=false, quoted=false, line=false, block=false;
            for(int i=0;i<sql.length();i++){
                char c=sql.charAt(i), n=i+1<sql.length()?sql.charAt(i+1):'\0';
                if(line){if(c=='\n'){line=false;out.append(' ');}continue;}
                if(block){if(c=='*'&&n=='/'){block=false;i++;out.append(' ');}continue;}
                if(!single&&!quoted&&c=='-'&&n=='-'){line=true;i++;continue;}
                if(!single&&!quoted&&c=='/'&&n=='*'){block=true;i++;continue;}
                if(!quoted&&c=='\'' && (i==0||sql.charAt(i-1)!='\\')){single=!single;out.append(' ');continue;}
                if(!single&&c=='\"' && (i==0||sql.charAt(i-1)!='\\')){quoted=!quoted;out.append(' ');continue;}
                out.append(single||quoted?' ':c);
            }
            if(single||quoted||block) throw new IllegalArgumentException("Unterminated SQL literal or comment");
            return out.toString();
        }
    }

    private static Tool tool(String name,String description,JsonNode schema,Call call){ToolDefinition definition=ToolDefinition.builder(name).description(description).parameters(schema).build();return new Tool(){public ToolDefinition definition(){return definition;}public String call(JsonNode args,ToolContext context)throws Exception{return call.run(args,context);}};}
    @FunctionalInterface private interface Call { String run(JsonNode args,ToolContext context)throws Exception; }
}
