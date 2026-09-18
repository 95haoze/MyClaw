package io.myclaw.tools;

import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolDefinition;
import io.myclaw.core.tool.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BuiltinTools} 的集成测试：工厂方法组合、注册表行为，以及所有内置工具定义的自洽性。
 */
class BuiltinToolsTest {

    @Test
    @DisplayName("safe() 只包含只读工具，不含写文件与执行命令")
    void safeExcludesWriteAndShell() {
        List<String> names = BuiltinTools.safe().stream().map(Tool::name).toList();

        assertThat(names)
                .containsExactly("calculate", "current_time", "read_file", "list_files", "search_text", "http_fetch")
                .doesNotContain("write_file", "run_command");
        assertThat(names.stream().filter("run_command"::equals)).isEmpty();
    }

    @Test
    @DisplayName("all() 包含全部 7 个工具，其中 ShellTool 默认禁用")
    void allContainsEveryTool() {
        List<Tool> tools = BuiltinTools.all();

        assertThat(tools).hasSize(24);
        assertThat(tools.stream().map(Tool::name))
                .containsExactlyInAnyOrder("calculate", "current_time", "read_file", "write_file",
                        "list_files", "search_text", "replace_text", "manage_file", "batch_replace_text", "git_status", "git_diff", "git_log", "git_add", "git_reset", "git_commit", "git_branch", "git_checkout", "find_symbol", "find_references", "code_outline", "code_diagnostics", "http_fetch", "execute_code", "run_command");

        Tool shell = tools.stream().filter(tool -> "run_command".equals(tool.name())).findFirst().orElseThrow();
        assertThat(shell.enabled()).isFalse();
    }

    @Test
    @DisplayName("registerAll(all()) 会跳过禁用的 ShellTool")
    void registeringAllSkipsDisabledShell() {
        ToolRegistry registry = new ToolRegistry().registerAll(BuiltinTools.all());

        assertThat(registry.size()).isEqualTo(22);
        assertThat(registry.contains("run_command")).isFalse();
        assertThat(registry.contains("write_file")).isTrue();
        assertThat(registry.names()).contains("calculate", "current_time", "read_file", "list_files", "http_fetch");
    }

    @Test
    @DisplayName("registry(includeShell, includeWrite) 按开关组合工具")
    void registryHonoursFlags() {
        ToolRegistry readOnly = BuiltinTools.registry(false, false);
        assertThat(readOnly.size()).isEqualTo(6);
        assertThat(readOnly.contains("write_file")).isFalse();
        assertThat(readOnly.contains("run_command")).isFalse();

        ToolRegistry shellOnly = BuiltinTools.registry(true, false);
        assertThat(shellOnly.contains("run_command")).isTrue();
        assertThat(shellOnly.contains("write_file")).isFalse();

        ToolRegistry full = BuiltinTools.registry(true, true);
        assertThat(full.size()).isEqualTo(23);
        assertThat(full.contains("run_command")).isTrue();
        assertThat(full.contains("write_file")).isTrue();
        assertThat(full.find("calculate")).isPresent();
    }

    @Test
    @DisplayName("所有工具的 definition() 参数 schema 非空、工具名不重复")
    void definitionsAreWellFormed() {
        List<Tool> tools = BuiltinTools.all();
        Set<String> names = new HashSet<>();

        for (Tool tool : tools) {
            ToolDefinition definition = tool.definition();
            assertThat(definition.name()).as("工具名").isNotBlank();
            assertThat(definition.description()).as("%s 的描述", definition.name()).isNotBlank();
            assertThat(names.add(definition.name())).as("工具名 %s 不应重复", definition.name()).isTrue();

            JsonNode parameters = definition.parameters();
            assertThat(parameters).as("%s 的参数 schema", definition.name()).isNotNull();
            assertThat(parameters.path("type").asString()).isEqualTo("object");
            assertThat(parameters.path("properties").isObject()).isTrue();
            assertThat(parameters.path("properties").size()).as("%s 的参数个数", definition.name()).isGreaterThan(0);
        }
        assertThat(names).hasSize(tools.size());
    }

    @Test
    @DisplayName("每个工具的必填参数都按约定声明")
    void requiredParametersAreDeclared() {
        Map<String, List<String>> expectedRequired = new LinkedHashMap<>();
        expectedRequired.put("calculate", List.of("expression"));
        expectedRequired.put("read_file", List.of("path"));
        expectedRequired.put("write_file", List.of("path", "content"));
        expectedRequired.put("manage_file", List.of("operation"));
        expectedRequired.put("batch_replace_text", List.of("edits"));
        expectedRequired.put("git_add", List.of("paths"));
        expectedRequired.put("git_reset", List.of("paths"));
        expectedRequired.put("git_commit", List.of("message"));
        expectedRequired.put("git_branch", List.of("action"));
        expectedRequired.put("git_checkout", List.of("branch"));
        expectedRequired.put("find_symbol", List.of("query"));
        expectedRequired.put("find_references", List.of("symbol"));
        expectedRequired.put("code_outline", List.of("path"));
        expectedRequired.put("http_fetch", List.of("url"));
        expectedRequired.put("run_command", List.of("command"));

        Map<String, Tool> byName = new LinkedHashMap<>();
        BuiltinTools.all().forEach(tool -> byName.put(tool.name(), tool));

        for (Map.Entry<String, List<String>> entry : expectedRequired.entrySet()) {
            JsonNode required = byName.get(entry.getKey()).definition().parameters().path("required");
            assertThat(required.isArray()).as("%s 的 required", entry.getKey()).isTrue();
            for (String field : entry.getValue()) {
                assertThat(required.toString()).as("%s 应包含必填参数 %s", entry.getKey(), field).contains(field);
            }
        }

        // 完全可选的工具不应声明 required
        assertThat(byName.get("current_time").definition().parameters().has("required")).isFalse();
    }

    @Test
    @DisplayName("shell(...) 工厂透传参数")
    void shellFactoryPassesArguments() {
        assertThat(BuiltinTools.shell(true, List.of("echo"), Duration.ofSeconds(5)).enabled()).isTrue();
        assertThat(BuiltinTools.shell(false, List.of(), Duration.ofSeconds(5)).enabled()).isFalse();
        assertThat(BuiltinTools.shell(true, List.of(), null).enabled()).isTrue();
    }

    @Test
    @DisplayName("各单例工厂返回独立实例")
    void factoriesReturnFreshInstances() {
        assertThat(BuiltinTools.calculator()).isNotSameAs(BuiltinTools.calculator());
        assertThat(BuiltinTools.calculator().name()).isEqualTo("calculate");
        assertThat(BuiltinTools.currentTime().name()).isEqualTo("current_time");
        assertThat(BuiltinTools.readFile().name()).isEqualTo("read_file");
        assertThat(BuiltinTools.writeFile().name()).isEqualTo("write_file");
        assertThat(BuiltinTools.listFiles().name()).isEqualTo("list_files");
        assertThat(BuiltinTools.httpFetch().name()).isEqualTo("http_fetch");
        assertThat(BuiltinTools.searchText().name()).isEqualTo("search_text");
        assertThat(BuiltinTools.replaceText().name()).isEqualTo("replace_text");
        assertThat(BuiltinTools.manageFile().name()).isEqualTo("manage_file");
        assertThat(BuiltinTools.batchReplaceText().name()).isEqualTo("batch_replace_text");
        assertThat(BuiltinTools.gitStatus().name()).isEqualTo("git_status");
        assertThat(BuiltinTools.gitDiff().name()).isEqualTo("git_diff");
        assertThat(BuiltinTools.gitLog().name()).isEqualTo("git_log");
        assertThat(BuiltinTools.gitAdd().name()).isEqualTo("git_add");
        assertThat(BuiltinTools.gitReset().name()).isEqualTo("git_reset");
        assertThat(BuiltinTools.gitCommit().name()).isEqualTo("git_commit");
        assertThat(BuiltinTools.gitBranch().name()).isEqualTo("git_branch");
        assertThat(BuiltinTools.gitCheckout().name()).isEqualTo("git_checkout");
        assertThat(BuiltinTools.findSymbol().name()).isEqualTo("find_symbol");
        assertThat(BuiltinTools.findReferences().name()).isEqualTo("find_references");
        assertThat(BuiltinTools.codeOutline().name()).isEqualTo("code_outline");
        assertThat(BuiltinTools.codeDiagnostics().name()).isEqualTo("code_diagnostics");
    }
}
