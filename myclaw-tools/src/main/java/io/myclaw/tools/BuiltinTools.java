package io.myclaw.tools;

import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolRegistry;

import java.time.Duration;
import java.util.List;

/**
 * 内置工具集的工厂类 —— 一站式拿到 MyClaw 开箱即用的工具。
 *
 * <p>典型用法：
 * <pre>{@code
 * // 只读、安全的组合（不含写文件与执行命令）
 * ToolRegistry registry = BuiltinTools.registry(false, false);
 *
 * // 全部内置工具：ShellTool 默认禁用，register 时会被自动跳过
 * new ToolRegistry().registerAll(BuiltinTools.all());
 * }</pre>
 */
public final class BuiltinTools {

    private BuiltinTools() {
    }

    /**
     * 全部内置工具（ShellTool 为默认禁用的实例，注册时会被 {@link ToolRegistry} 自动跳过）。
     *
     * @return 不可变工具列表
     */
    public static List<Tool> all() {
        return List.of(calculator(), currentTime(), readFile(), writeFile(), listFiles(), searchText(), replaceText(), manageFile(), batchReplaceText(), gitStatus(), gitDiff(), gitLog(), gitAdd(), gitReset(), gitCommit(), gitBranch(), gitCheckout(), findSymbol(), findReferences(), codeOutline(), codeDiagnostics(), httpFetch(), new CodeExecuteTool(), new ShellTool());
    }

    /**
     * 只读安全工具集：不含任何能写盘或执行命令的工具。
     *
     * @return 计算 + 时间 + 读文件 + 列目录 + HTTP 抓取
     */
    public static List<Tool> safe() {
        return List.of(calculator(), currentTime(), readFile(), listFiles(), searchText(), httpFetch());
    }

    /** 计算器工具（calculate）。 */
    public static CalculatorTool calculator() {
        return new CalculatorTool();
    }

    /** 当前时间工具（current_time）。 */
    public static CurrentTimeTool currentTime() {
        return new CurrentTimeTool();
    }

    /** 读文件工具（read_file）。 */
    public static FileReadTool readFile() {
        return new FileReadTool();
    }

    /** 写文件工具（write_file）。 */
    public static FileWriteTool writeFile() {
        return new FileWriteTool();
    }

    /** 列目录工具（list_files）。 */
    public static FileListTool listFiles() {
        return new FileListTool();
    }

    /** HTTP 抓取工具（http_fetch）。 */
    public static HttpFetchTool httpFetch() {
        return new HttpFetchTool();
    }

    /** 搜索文本工具（search_text）。*/
    public static SearchTextTool searchText() {
        return new SearchTextTool();
    }

    /** 文本替换具（replace_text）。*/
    public static ReplaceTextTool replaceText() {
        return new ReplaceTextTool();
    }

    public static FileManageTool manageFile() { return new FileManageTool(); }

    public static BatchReplaceTextTool batchReplaceText() { return new BatchReplaceTextTool(); }

    public static Tool gitStatus() { return GitTools.status(); }
    public static Tool gitDiff() { return GitTools.diff(); }
    public static Tool gitLog() { return GitTools.log(); }
    public static Tool gitAdd() { return GitTools.add(); }
    public static Tool gitReset() { return GitTools.reset(); }
    public static Tool gitCommit() { return GitTools.commit(); }
    public static Tool gitBranch() { return GitTools.branch(); }
    public static Tool gitCheckout() { return GitTools.checkout(); }
    public static Tool findSymbol() { return JavaCodeTools.findSymbol(); }
    public static Tool findReferences() { return JavaCodeTools.findReferences(); }
    public static Tool codeOutline() { return JavaCodeTools.outline(); }
    public static Tool codeDiagnostics() { return JavaCodeTools.diagnostics(); }
    public static Tool lspQuery(java.util.Map<String, java.util.List<String>> commands) { return new LspQueryTool(commands); }
    public static Tool webSearch(java.util.Map<String, NetworkTools.SearchProvider> providers, java.util.Map<String, NetworkTools.CredentialProfile> credentials) { return NetworkTools.webSearch(providers, credentials); }
    public static Tool apiRequest(java.util.Map<String, NetworkTools.CredentialProfile> credentials) { return NetworkTools.apiRequest(credentials); }
    public static Tool downloadFile(java.util.Map<String, NetworkTools.CredentialProfile> credentials) { return NetworkTools.downloadFile(credentials); }
    public static Tool batchFetch(java.util.Map<String, NetworkTools.CredentialProfile> credentials) { return NetworkTools.batchFetch(credentials); }
    public static Tool databaseSchema(java.util.Map<String, DatabaseTools.Profile> profiles) { return DatabaseTools.schema(profiles); }
    public static Tool databaseQuery(java.util.Map<String, DatabaseTools.Profile> profiles) { return DatabaseTools.query(profiles); }
    public static Tool databaseExecute(java.util.Map<String, DatabaseTools.Profile> profiles) { return DatabaseTools.execute(profiles); }


    public static CodeExecuteTool executeCode(boolean enabled) {
        return new CodeExecuteTool(enabled);
    }

    /**
     * 构造一个 Shell 工具。
     *
     * @param enabled           是否启用
     * @param allowlistPrefixes 允许的命令前缀，空列表表示不限制前缀（仍拦截高危片段）
     * @param timeout           超时时间
     */
    public static ShellTool shell(boolean enabled, List<String> allowlistPrefixes, Duration timeout) {
        return new ShellTool(enabled, allowlistPrefixes, timeout);
    }

    /**
     * 组装一个已注册完毕的工具注册表。
     *
     * @param includeShell 是否包含并启用 {@code run_command}
     * @param includeWrite 是否包含 {@code write_file}
     * @return 已 {@code registerAll} 的 {@link ToolRegistry}
     */
    public static ToolRegistry registry(boolean includeShell, boolean includeWrite) {
        ToolRegistry registry = new ToolRegistry();
        registry.register(calculator());
        registry.register(currentTime());
        registry.register(readFile());
        registry.register(listFiles());
        registry.register(searchText());
        registry.register(httpFetch());
        if (includeWrite) {
            registry.register(writeFile());
            registry.register(replaceText());
            registry.register(manageFile());
            registry.register(batchReplaceText());
            registry.register(gitStatus()); registry.register(gitDiff()); registry.register(gitLog());
            registry.register(gitAdd()); registry.register(gitReset()); registry.register(gitCommit());
            registry.register(gitBranch()); registry.register(gitCheckout());
            registry.register(findSymbol()); registry.register(findReferences());
            registry.register(codeOutline()); registry.register(codeDiagnostics());
        }
        if (includeShell) {
            registry.register(new ShellTool(true, List.of(), ShellTool.DEFAULT_TIMEOUT));
        }
        return registry;
    }
}
