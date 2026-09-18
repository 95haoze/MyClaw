package io.myclaw.tools;

import io.myclaw.core.json.Json;
import io.myclaw.core.tool.ToolContext;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class DatabaseToolsTest {
    @TempDir Path workspace;
    JdbcDataSource dataSource;

    @BeforeEach
    void database() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("create table users (id bigint primary key, name varchar(100) not null)");
            statement.execute("create index idx_users_name on users(name)");
            statement.execute("insert into users(id,name) values (1,'Ada'),(2,'Linus'),(3,'Grace')");
        }
    }

    @Test
    void inspectsSchemaAndRunsParameterizedBoundedQuery() throws Exception {
        var profiles = Map.of("main", new DatabaseTools.Profile(dataSource, false, 2, 5));
        String schema = DatabaseTools.schema(profiles).call(Json.obj().put("profile", "main").put("table", "USERS"), context());
        assertThat(schema).contains("USERS", "ID", "PRIMARY KEY", "IDX_USERS_NAME");

        String result = DatabaseTools.query(profiles).call(Json.obj().put("profile", "main")
                .put("sql", "select id,name from users where id > ? order by id")
                .set("parameters", Json.arr().add(0)).put("maxRows", 2), context());
        assertThat(result).contains("Ada", "Linus", "Rows: 2", "truncated").doesNotContain("Grace");
    }

    @Test
    void writeRequiresExplicitPermissionAndCommitsDml() throws Exception {
        var readOnly = Map.of("main", new DatabaseTools.Profile(dataSource, false, 100, 5));
        assertThatThrownBy(() -> DatabaseTools.execute(readOnly).call(Json.obj().put("sql", "delete from users where id = 1"), context()))
                .isInstanceOf(SecurityException.class).hasMessageContaining("read-only");

        var writable = Map.of("main", new DatabaseTools.Profile(dataSource, true, 100, 5));
        String result = DatabaseTools.execute(writable).call(Json.obj().put("sql", "update users set name = ? where id = ?")
                .set("parameters", Json.arr().add("Updated").add(1)), context());
        assertThat(result).contains("affected rows: 1");
        assertThat(DatabaseTools.query(writable).call(Json.obj().put("sql", "select name from users where id = 1"), context()))
                .contains("Updated");
    }

    @Test
    void rejectsDdlMultipleStatementsAndWriteDisguisedAsQuery() {
        assertThatThrownBy(() -> DatabaseTools.SqlSafety.requireReadOnly("drop table users"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> DatabaseTools.SqlSafety.requireReadOnly("select * from users; delete from users"))
                .isInstanceOf(SecurityException.class).hasMessageContaining("semicolons");
        assertThatCode(() -> DatabaseTools.SqlSafety.requireReadOnly("select * from users /* ; drop table users */"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> DatabaseTools.SqlSafety.requireDml("create table x(id int)"))
                .isInstanceOf(SecurityException.class);
    }

    private ToolContext context() { return ToolContext.of(workspace); }
}
